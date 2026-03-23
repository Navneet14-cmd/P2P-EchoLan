package com.robustians.utils;

import java.net.*;

public class NetworkUtils {

    public static String getLocalIpFromInterface(String interfaceName) {
        try {
            NetworkInterface ni = NetworkInterface.getByName(interfaceName);

            if (ni == null || !ni.isUp()) {
                return null; // Interface doesn't exist or is down
            }

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();

                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null; // No usable IP found
    }
}