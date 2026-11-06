package com.ChattingSystem.realtime;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.security.jwt.JwtService;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Đầu-cuối qua WebSocket thật: CONNECT bằng JWT, SUBSCRIBE topic phòng, SEND tin, nhận MESSAGE.
 * Dữ liệu được commit thật (không rollback) nên dọn ở {@link #cleanUp()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompMessagingTest {

    @LocalServerPort private int port;
    @Autowired private ChatRoomService chatRoomService;
    @Autowired private UserRepository userRepo;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private User owner;
    private User member;
    private User stranger;
    private Long roomId;

    private User newUser(String name) {
        String id = UUID.randomUUID().toString();
        User user = userRepo.saveAndFlush(User.create("it-uid-" + id, "it-" + id + "@example.com", name, null, null));
        userIds.add(user.getId());
        return user;
    }

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        owner = newUser("Chu phong IT");
        member = newUser("Thanh vien IT");
        stranger = newUser("Nguoi la IT");
        roomId = chatRoomService.createRoom(owner.getId(),
                new CreateRoomRequest("Phong IT", null, 21.0285, 105.8542, 60, 1000)).id();
        chatRoomService.joinRoom(member.getId(), roomId, new LocationRequest(21.0285, 105.8542));
    }

    @AfterEach
    void cleanUp() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        stompClient.stop();
        jdbc.update("DELETE FROM chat_rooms WHERE id = ?", roomId);
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    private StompSession connect(String token) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/api/v1/ws", handshake, connect, new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        return queue;
    }

    @Test
    @SuppressWarnings("unchecked")
    void member_sendsMessage_andRoomTopicReceivesIt() throws Exception {
        StompSession ownerSession = connect(jwtService.generateAccessToken(owner));
        StompSession memberSession = connect(jwtService.generateAccessToken(member));
        BlockingQueue<Map<String, Object>> ownerTopic = subscribe(ownerSession, "/topic/rooms." + roomId);
        BlockingQueue<Map<String, Object>> memberErrors = subscribe(memberSession, "/user/queue/errors");
        Thread.sleep(300); // chờ SUBSCRIBE tới broker

        UUID clientId = UUID.randomUUID();
        memberSession.send("/app/rooms." + roomId + ".send",
                Map.of("clientMessageId", clientId.toString(), "content", "Chào cả nhà"));

        Map<String, Object> event = ownerTopic.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("MESSAGE");
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        assertThat(data.get("content")).isEqualTo("Chào cả nhà");
        assertThat(data.get("clientMessageId")).isEqualTo(clientId.toString());
        assertThat(((Map<String, Object>) data.get("sender")).get("fullName")).isEqualTo("Thanh vien IT");
        assertThat(data.get("createdAt")).isInstanceOf(String.class);
        assertThat(memberErrors).isEmpty();
    }

    @Test
    void invalidMessage_isReportedOnUserErrorQueue() throws Exception {
        StompSession memberSession = connect(jwtService.generateAccessToken(member));
        BlockingQueue<Map<String, Object>> errors = subscribe(memberSession, "/user/queue/errors");
        Thread.sleep(300);

        memberSession.send("/app/rooms." + roomId + ".send",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", ""));

        Map<String, Object> error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("statusCode")).isEqualTo(400);
    }

    @Test
    void stranger_subscribingToRoom_getsErrorAndStaysConnected() throws Exception {
        StompSession strangerSession = connect(jwtService.generateAccessToken(stranger));
        BlockingQueue<Map<String, Object>> errors = subscribe(strangerSession, "/user/queue/errors");
        Thread.sleep(300);

        BlockingQueue<Map<String, Object>> topic = subscribe(strangerSession, "/topic/rooms." + roomId);

        Map<String, Object> error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("statusCode")).isEqualTo(403);
        assertThat(error.get("path")).isEqualTo("/topic/rooms." + roomId);
        assertThat(strangerSession.isConnected()).isTrue();

        // Không nhận được tin của phòng
        StompSession memberSession = connect(jwtService.generateAccessToken(member));
        memberSession.send("/app/rooms." + roomId + ".send",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "Bí mật"));
        assertThat(topic.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connect_withoutToken_isRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }
}
