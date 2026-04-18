package com.example.hldsn.incident_report_module;

import java.util.Arrays;

public final class ChatIdUtil {

    private ChatIdUtil() {
    }

    public static String buildChatId(String uidA, String uidB) {
        String first = uidA != null ? uidA : "";
        String second = uidB != null ? uidB : "";
        String[] uids = {first, second};
        Arrays.sort(uids);
        return uids[0] + "_" + uids[1];
    }
}
