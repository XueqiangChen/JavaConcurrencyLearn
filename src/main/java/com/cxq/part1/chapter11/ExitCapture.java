package com.cxq.part1.chapter11;

/**
 * kill -9 的时候不会执行hook，kill -9 是强制退出
 */
public class ExitCapture {
    public static void main(String[] args) {
        // Runtime 可以拿到cpu，memory等系统资源，也可以执行shell脚本
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("The application will be exit.");
            notifyAndRelease();
        }));
//         int i = 0;
        while (true) {
            try {
                Thread.sleep(1_000L);
                System.out.println("I am working...");
            } catch (InterruptedException e) {
                //ignore
            }
            // 自动退出
//            i++;
//            if (i > 20) {
//             throw new RuntimeException("Error");
//            }
        }
    }

    private static void notifyAndRelease() {
        System.out.println("notify to the admin.");
        try {
            Thread.sleep(1_000L);
        } catch (InterruptedException e) {
            //ignore
        }
        System.out.println("Will release resource(socket, file, connection.)");
        try {
            Thread.sleep(1_000L);
        } catch (InterruptedException e) {
            // ignore
        }
        System.out.println("Release and Notify Done!");
    }
}
