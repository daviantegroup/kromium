package dev.daviante.kromium.sample.swing.model;

import dev.daviante.kromium.presentation.handler.KromiumDownloadItem;

/**
 * Model representing a download entry in the Swing Download Manager.
 */
public class DownloadEntry {
    private final int id;
    private final String url;
    private final String fileName;
    private String fullPath;
    private int percent;
    private long speed;
    private long receivedBytes;
    private long totalBytes;
    private boolean inProgress;
    private boolean complete;
    private boolean canceled;
    private boolean paused;

    public DownloadEntry(KromiumDownloadItem item) {
        this.id = item.getId();
        this.url = item.getUrl();
        this.fileName = item.getSuggestedFileName().isBlank() ? "download" : item.getSuggestedFileName();
        this.fullPath = item.getFullPath();
        update(item);
    }

    public void update(KromiumDownloadItem item) {
        this.percent = item.getPercentComplete();
        this.speed = item.getSpeed();
        this.receivedBytes = item.getReceivedBytes();
        this.totalBytes = item.getTotalBytes();
        this.inProgress = item.isInProgress();
        this.complete = item.isComplete();
        this.canceled = item.isCanceled();
        this.paused = item.isPaused();
        if (item.getFullPath() != null && !item.getFullPath().isBlank()) {
            this.fullPath = item.getFullPath();
        }
    }

    public int getId() { return id; }
    public String getUrl() { return url; }
    public String getFileName() { return fileName; }
    public String getFullPath() { return fullPath; }
    public int getPercent() { return percent; }
    public long getSpeed() { return speed; }
    public long getReceivedBytes() { return receivedBytes; }
    public long getTotalBytes() { return totalBytes; }
    public boolean isInProgress() { return inProgress; }
    public boolean isComplete() { return complete; }
    public boolean isCanceled() { return canceled; }
    public boolean isPaused() { return paused; }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            this.speed = 0;
        }
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
        if (canceled) {
            this.inProgress = false;
            this.paused = false;
            this.speed = 0;
        }
    }

    public String getStatusText() {
        if (complete) return "Completed";
        if (canceled) return "Canceled";
        if (paused) return String.format("Paused (%d%%)", percent);
        if (inProgress) {
            double speedKb = speed / 1024.0;
            if (speedKb > 1024.0) {
                return String.format("%d%% (%.1f MB/s)", percent, speedKb / 1024.0);
            }
            return String.format("%d%% (%.0f KB/s)", percent, speedKb);
        }
        return "Pending";
    }
}
