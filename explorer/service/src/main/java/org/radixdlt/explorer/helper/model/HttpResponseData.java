package org.radixdlt.explorer.helper.model;

/**
 * Represents a successfully read response body along with its
 * content type.
 */
public class HttpResponseData {
    private final String content;
    private final String contentType;

    /**
     * Creates a new instance of this class.
     *
     * @param content     The successfully read response body.
     * @param contentType The type of the response body.
     */
    public HttpResponseData(String content, String contentType) {
        this.content = content;
        this.contentType = contentType;
    }

    /**
     * @return The successfully read response body.
     */
    public String getContent() {
        return content;
    }

    /**
     * @return The content type of the response body.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @return Whether the content type starts with 'application/json' or not.
     */
    public boolean isJson() {
        return contentType != null && contentType.toLowerCase().startsWith("application/json");
    }

    /**
     * @return Whether the content type starts with 'text/plain' or not.
     */
    public boolean isPlainText() {
        return contentType != null && contentType.toLowerCase().startsWith("text/plain");
    }

}
