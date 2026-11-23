package com.moviemax.app;
public class DownloadRulesTest {
    private static void check(boolean result) { if(!result) throw new AssertionError(); }
    public static void main(String[] args) {
        check(DownloadRules.autoResume("network"));
        check(!DownloadRules.autoResume("paused"));
        check(DownloadRules.recoverAfterRestart("running"));
        check(DownloadRules.recoverAfterRestart("network"));
        check(!DownloadRules.recoverAfterRestart("paused"));
        check(!DownloadRules.recoverAfterRestart("done"));
        check(DownloadRules.member(2000,1000));
        check(!DownloadRules.member(1000,1000));
        check(!DownloadRules.member(0,1000));
        System.out.println("Passed: network auto-resume, manual pause, process restart, subscription expiry");
    }
}
