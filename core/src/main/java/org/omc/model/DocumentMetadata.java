// filepath: src/main/java/org/omc/model/DocumentMetadata.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for document files.
 * Requirement REQ-002.2: Document file metadata including page count, author,
 * and title.
 */
public final class DocumentMetadata implements MediaMetadata {

    private final int pageCount; // Number of pages
    private final String title; // Document title
    private final String author; // Document author
    private final String subject; // Document subject
    private final String creator; // Application that created the document

    @JsonCreator
    public DocumentMetadata(
            @JsonProperty("pageCount") int pageCount,
            @JsonProperty("title") String title,
            @JsonProperty("author") String author,
            @JsonProperty("subject") String subject,
            @JsonProperty("creator") String creator) {
        this.pageCount = pageCount;
        this.title = title;
        this.author = author;
        this.subject = subject;
        this.creator = creator;
    }

    /**
     * Creates a DocumentMetadata builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @JsonIgnore
    public FormatCategory getCategory() {
        return FormatCategory.DOCUMENT;
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return pageCount >= 0; // Page count of 0 is valid for empty documents
    }

    @Override
    @JsonIgnore
    public String getSummary() {
        StringBuilder summary = new StringBuilder();

        if (title != null && !title.isBlank()) {
            summary.append(title).append(", ");
        }

        summary.append(pageCount).append(pageCount == 1 ? " page" : " pages");

        if (author != null && !author.isBlank()) {
            summary.append(", by ").append(author);
        }

        return summary.toString();
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getSubject() {
        return subject;
    }

    public String getCreator() {
        return creator;
    }

    /**
     * Checks if this document has metadata (title, author, or subject).
     */
    @JsonIgnore
    public boolean hasMetadata() {
        return (title != null && !title.isBlank()) ||
                (author != null && !author.isBlank()) ||
                (subject != null && !subject.isBlank());
    }

    /**
     * Checks if this is a multi-page document (> 1 page).
     */
    @JsonIgnore
    public boolean isMultiPage() {
        return pageCount > 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DocumentMetadata that = (DocumentMetadata) o;
        return pageCount == that.pageCount &&
                Objects.equals(title, that.title) &&
                Objects.equals(author, that.author) &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(creator, that.creator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageCount, title, author, subject, creator);
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "pageCount=" + pageCount +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", subject='" + subject + '\'' +
                ", creator='" + creator + '\'' +
                '}';
    }

    /**
     * Builder for DocumentMetadata.
     */
    public static class Builder {
        private int pageCount;
        private String title;
        private String author;
        private String subject;
        private String creator;

        private Builder() {
        }

        public Builder pageCount(int pageCount) {
            this.pageCount = pageCount;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder creator(String creator) {
            this.creator = creator;
            return this;
        }

        public DocumentMetadata build() {
            return new DocumentMetadata(pageCount, title, author, subject, creator);
        }
    }
}
