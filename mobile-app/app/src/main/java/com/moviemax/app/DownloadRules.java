package com.moviemax.app;
/** Pure rules shared with the native regression tests. */
final class DownloadRules {
    static boolean member(long until, long now) { return until > now; }
    static boolean autoResume(String status) { return "network".equals(status); }
    static boolean recoverAfterRestart(String status) { return "running".equals(status) || "queued".equals(status) || "network".equals(status); }
}
