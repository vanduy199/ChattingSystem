-- Blog post bodies switch from Markdown to sanitized HTML (Tiptap editor)
ALTER TABLE posts
    RENAME COLUMN content_markdown TO content_html;
