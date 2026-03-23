package com.robustians.utils;

import java.net.*;
import java.util.*;

public class NetworkSelector {

    public static NetworkInterface chooseInterface() {
        System.out.println("Available Network Interfaces:");

        try {
            List<NetworkInterface> validInterfaces = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            int index = 1;

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
                    continue;

                // get IPv4
                String ip = null;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr instanceof Inet4Address) {
                        ip = addr.getHostAddress();
                        break;
                    }
                }

                if (ip == null)
                    continue;

                validInterfaces.add(ni);

                System.out.println(index + ". " + ni.getDisplayName() + " (" + ip + ")");
                index++;
            }

            if (validInterfaces.isEmpty()) {
                System.out.println("No usable interfaces found");
                return null;
            }

            // user input
            Scanner sc = new Scanner(System.in);
            System.out.print("\nSelect interface: ");
            int choice = sc.nextInt();
            // int choice = 2;  //  TODO: Remove

            if (choice < 1 || choice > validInterfaces.size()) {
                System.out.println("Invalid choice");
                return null;
            }

            return validInterfaces.get(choice - 1);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getIp(NetworkInterface ni) {
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
            InetAddress addr = ia.getAddress();
            if (addr instanceof Inet4Address) {
                return addr.getHostAddress();
            }
        }
        return null;
    }
}