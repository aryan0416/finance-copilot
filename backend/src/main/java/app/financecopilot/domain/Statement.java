package app.financecopilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Upload metadata. {@code fileHash} (SHA-256 of the bytes) rejects re-uploading the identical file. */
@Entity
@Table(name = "statements")
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    @Column(name = "file_hash", nullable = false, columnDefinition = "bpchar(64)")
    private String fileHash;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_duplicate", nullable = false)
    private int rowsDuplicate;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    protected Statement() {
    }

    public Statement(Long userId, String fileName, FileType fileType, String fileHash) {
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileHash = fileHash;
    }

    public void recordResult(int imported, int duplicates, int failed, LocalDate start, LocalDate end) {
        this.rowsImported = imported;
        this.rowsDuplicate = duplicates;
        this.rowsFailed = failed;
        this.periodStart = start;
        this.periodEnd = end;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public int getRowsImported() {
        return rowsImported;
    }

    public int getRowsDuplicate() {
        return rowsDuplicate;
    }

    public int getRowsFailed() {
        return rowsFailed;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
