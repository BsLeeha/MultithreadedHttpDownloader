/*
 * TODO: https, proxy, ed2k(msdn i tell you), how fiddler decrypt https
 */

import org.apache.commons.cli.*;

import javax.net.ssl.SSLException;
import javax.sound.midi.Soundbank;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class HttpDownloader {
    // basic information
    private String url;
    private String rawFileName;
    private String storeFileName;

    // for connection
    private static final int TIME_OUT = 3000;

    // for multithreading
    private static final int DEFAULT_THREAD_NUMBER = 5;
    private boolean resumable = false;
    private boolean multithreaded = false;
    private int threadNumber = DEFAULT_THREAD_NUMBER;
    private AtomicInteger workingThreads = new AtomicInteger(0);
    private AtomicBoolean finished = new AtomicBoolean(false);

    // for progress bar
    private volatile long fileSize = 0;
    private AtomicLong downloadedBytes = new AtomicLong();
    private AtomicLongArray partialFileSize = new AtomicLongArray(threadNumber);
    private AtomicLongArray downloadedSize = new AtomicLongArray(threadNumber);

    public static void main(String[] args) {

        HttpDownloader downloader = new HttpDownloader();

        // set and parse command line
        Options options = downloader.setCommandLine();
        downloader.parseCommandLine(args, options);

        // check multithreaded
        downloader.checkMultithreaded();

        // show starting task info
        downloader.showTaskStartInfo();

        long startTime = System.currentTimeMillis();

        // allocate download threads and download
        downloader.allocateDownload();

        // start monitor
        new Thread(downloader.new MonitorTask()).start();

        // wait downading
        synchronized (downloader.finished) {
            try {
                downloader.finished.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        if (downloader.multithreaded)
            downloader.mergeTempFiles();

        // show ending task info
        downloader.showTaskEndInfo(elapsedTime);

    }

    public void showTaskStartInfo() {

        System.out.println("*** Task information: ");
        System.out.println("*** File to be download:\t" + rawFileName);
        System.out.println("*** File save to:\t\t\t" + new File(storeFileName).getAbsolutePath());
        System.out.println("*** File size:\t\t\t\t" + unify(fileSize));
        System.out.println("*** Support multithread?\t" + multithreaded);
        System.out.println();
        System.out.println("*** Start download with " + threadNumber + " threads...");
        System.out.println();

    }

    public void showTaskEndInfo(long time) {

        System.out.println();

        if (downloadedBytes.get() != fileSize)
            System.out.println("Download was not complete!");
        else
            System.out.println("\n*** File has been downloaded successfully!");

        System.out.println("*** Have a nice day!");
        System.out.printf("*** Time Used: %.3f s, Average speed: %d kB/s\n",
                (float)time/1000, downloadedBytes.get()/time);

    }

    public void mergeTempFiles() {

        try (OutputStream out = new FileOutputStream(storeFileName, true)) {

            for (int i = 0; i < threadNumber; i++) {
                String tempFileName = storeFileName + "." + i + ".tmp";

                try (InputStream in = new FileInputStream(tempFileName)) {
                    byte[] bytes = new byte[2048];
                    int length = 0;

                    while ((length = in.read(bytes)) != -1) {
                        out.write(bytes, 0, length);
                        out.flush();
                    }

                }

                new File(tempFileName).delete();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isResumable() {

        int resposeCode = 0;

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestProperty("Range", "bytes=0-");

            connection.connect();

            resposeCode = connection.getResponseCode();
            fileSize = connection.getContentLength();

            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }

        // 206: partial content
        return resposeCode == 206;
    }

    public void checkMultithreaded() {
        if (threadNumber != 1 && isResumable())
            multithreaded = true;
        else {
            threadNumber = 1;
            multithreaded = false;
        }
    }

    public void allocateDownload() {
        if (multithreaded) {
            long blockSize = fileSize / threadNumber;

            for (int i = 0; i < threadNumber; i++) {
                if (i == threadNumber - 1)
                    new Thread(new DownloadTask(i, blockSize * i, fileSize - 1)).start();
                else
                    new Thread(new DownloadTask(i, blockSize * i, blockSize * (i+1) - 1)).start();
            }

        } else {
            new Thread(new DownloadTask(-1, 0, fileSize - 1)).start();
        }
    }

    class DownloadTask implements Runnable {

        private int id;
        private long start;
        private long end;           // download interval: [start, end]
        private File tempFile;

        public DownloadTask(int id, long start, long end) {
            this.id = id;
            this.start = start;
            this.end = end;
            if (id != -1)
                tempFile = new File(storeFileName + "." + id + ".tmp");
            else
                tempFile = new File(storeFileName);

            workingThreads.incrementAndGet();
            try {
                partialFileSize.addAndGet(id, end - start + 1);
            }catch (NullPointerException e){
                System.out.println(id);
            }
        }

        @Override
        public void run() {
            boolean success = false;

            do {
                success = connectDownload();
            } while (!success);

            workingThreads.decrementAndGet();
        }

        public boolean connectDownload() {

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

                connection.setRequestProperty("Range", String.format("bytes=%d-%d", start, end));
                connection.setConnectTimeout(TIME_OUT);
                connection.setReadTimeout(TIME_OUT);

                connection.connect();

                if (connection.getContentLength() != end - start + 1)
                    return false;

                try (InputStream in = connection.getInputStream()) {
                    try (OutputStream out = new FileOutputStream(tempFile)) {
                        byte[] bytes = new byte[2048];
                        int length;

                        while ((length = in.read(bytes)) != -1) {
                            out.write(bytes, 0, length);
                            out.flush();
                            downloadedBytes.addAndGet(length);
                            downloadedSize.addAndGet(id, length);
                            start += length;                    // real time update
                        }
                    }
                }

                connection.disconnect();

            } catch (SocketTimeoutException e) {
//                System.out.println("Part " + id + " reading timeout. Check your network. Retry Now...");
                return false;
            } catch (SSLException e) {
//                System.out.println("Part " + id + e.getMessage() + " Retry Now...");
                return false;
            } catch (IOException e) {
//                System.out.println("Part" + id + " encountered io error.");
                return false;
            }

            return start > end;
        }
    }

    class MonitorTask implements Runnable {
        @Override
        public void run() {
            long prevBytes = 0;
            long currBytes = 0;
            long speed;

            while (true) {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                currBytes = downloadedBytes.get();
                speed = (currBytes - prevBytes) >> 10;      // KB/s
                prevBytes = currBytes;

//                System.out.printf("Downloading %.3f%%(%s/%s), working threads: %d, speed: %s/s\n",
//                                  (float)downloadedBytes.get()/fileSize*100, unify(downloadedBytes.get()), unify(fileSize),
//                                  workingThreads.get(), unify(speed*1000));

                ProgressBar bar = new ProgressBar(partialFileSize);
                bar.drawProgressBar(downloadedSize, unify(speed*1000)+"/s");

                if (workingThreads.get() == 0) {
                    synchronized (finished) {
                        finished.notifyAll();
                    }

                    break;
                }
            }
        }
    }

    public static String unify(long num) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        float temp = num;
        for (String unit : units) {
            if (temp < 1024) return String.format("%.2f %s", temp, unit);
            else temp /= 1024;
        }
        return null;
    }

    public Options setCommandLine() {
        Options options = new Options();

        Option urlOption = new Option("u", "url", true, "the url to download");
        urlOption.setRequired(true);
        options.addOption(urlOption);

        Option threadNumberOption = new Option("t", "threadNumber", true,
                "set thread number to download");
        threadNumberOption.setRequired(false);
        options.addOption(threadNumberOption);

        Option fileNameOption = new Option("f", "fileName", true, "save to file");
        fileNameOption.setRequired(false);
        options.addOption(fileNameOption);

        return options;
    }

    public void parseCommandLine(String[] args, Options options) {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();
        CommandLine cmd = null;
        String threadNumStr = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            helpFormatter.printHelp("HttpDownloader", options);

            System.exit(1);
        }

        url = cmd.getOptionValue("url");

        if ((threadNumStr = cmd.getOptionValue("threadNumber")) != null) {
            threadNumber = Integer.valueOf(threadNumStr);
            checkThreadNumber();
        }

        storeFileName = cmd.getOptionValue("fileName");
        rawFileName = url.substring(url.lastIndexOf("/") + 1);
        if (storeFileName == null) storeFileName = rawFileName;
//        File file = new File(fileName);
//        file.getParentFile().mkdirs();
//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    public void checkThreadNumber() {
        if (threadNumber < 1 || threadNumber > 10) {
            threadNumber = DEFAULT_THREAD_NUMBER;
            System.err.println("thread number assigned is not valid:" + threadNumber +
                    ",reset to: " + DEFAULT_THREAD_NUMBER);
        }
    }

}
