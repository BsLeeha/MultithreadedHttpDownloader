import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

public class ProgressBar {
    private AtomicLongArray fileSize;

    private int barNumber = 20;

    public ProgressBar(AtomicLongArray fileSize) { this.fileSize = fileSize; }

    public void drawProgressBar( AtomicLongArray downloadedSize, String speed ) {

        StringBuilder theLine = new StringBuilder("[");

        Long sum1 = 0L, sum2 = 0L;

        for (int i = 0; i < downloadedSize.length(); i++)
            sum1 += downloadedSize.get(i);

        for (int i = 0; i < fileSize.length(); i++)
            sum2 += fileSize.get(i);

        double totalRatio = (double)sum1/sum2;

        for (int i = 0; i < downloadedSize.length(); i++) {
            double ratio = (double)downloadedSize.get(i) / fileSize.get(i);
            int barId = (int) (ratio * barNumber);

            for (int j = 1; j <= barNumber; j++) {
                if (j == barId) theLine.append(">");
                else if (j < barId) theLine.append("-");
                else theLine.append(" ");
            }

            if (i != downloadedSize.length() - 1) theLine.append("|");
        }

        theLine.append("]");

        System.out.printf("\r%s %.2f%%   %s", theLine.toString(), totalRatio*100, speed);
    }
}