package repopulse.server.analytics.statistics;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public final class Statistics
{
    private Statistics() { }

    public static double median(List<Integer> sample)
    {
        Collections.sort(sample);
        int size = sample.size();

        if (size == 0)
            throw new IllegalArgumentException();

        if (size % 2 != 0)
           return (double) sample.get(size / 2);

        return (sample.get(size / 2 - 1) + sample.get(size / 2)) / 2d;
    }

    public static Double roundToHundredth(Double number)
    {
        if (number == null)
            return null;

        double scale = Math.pow(10, 2);
        return Math.round(number * scale) / scale;
    }

    public static Integer percentile(List<Integer> sample, Double percentile)
    {
        if (sample.size() < 20)
            return null;

        Collections.sort(sample);

        int percentileIndex = (int) Math.ceil(percentile * sample.size()) - 1;

        return sample.get(percentileIndex);
    }

    public static Double spearmanCorrelation(List<? extends Number> firstSample,
                                             List<? extends Number> secondSample)
    {
        double[] firstSampleRanks = ranks(firstSample);
        double[] secondSampleRanks = ranks(secondSample);

        return pearsonCorrelation(firstSampleRanks, secondSampleRanks);
    }

    private static double[] ranks(List<? extends Number> sample)
    {
        List<IndexedValue> sortedSample = IntStream.range(0, sample.size())
                .mapToObj(x -> new IndexedValue(
                        x,
                        sample.get(x).doubleValue()))
                .sorted(Comparator.comparingDouble(IndexedValue::value))
                .toList();

        double[] ranks = new double[sortedSample.size()];

        int start = 0;

        while (start < sortedSample.size())
        {
            int end = start + 1;

            while (end < sortedSample.size() &&
                    sortedSample.get(start).value == sortedSample.get(end).value)
            {
                end++;
            }

            double rank = (start + 1 + end) / 2.0;

            for (int i = start; i < end; i++)
            {
                int originalIndex = sortedSample.get(i).originalIndex;
                ranks[originalIndex] = rank;
            }

            start = end;
        }

        return ranks;
    }

    private static Double pearsonCorrelation(double[] firstSample,
                                             double[] secondSample)
    {
        if (firstSample.length != secondSample.length)
            throw new IllegalArgumentException("Samples must have the same size");

        int length = firstSample.length;

        if (length < 2)
            return null;

        double firstAverage = Arrays.stream(firstSample).sum() / length;
        double secondAverage = Arrays.stream(secondSample).sum() / length;

        double numerator = 0, firstSquaredDeviationSum = 0, secondSquaredDeviationSum = 0;

        for (int i = 0; i < length; i++)
        {
            numerator += (firstSample[i] - firstAverage) * (secondSample[i] - secondAverage);
            firstSquaredDeviationSum += Math.pow(firstSample[i] - firstAverage, 2);
            secondSquaredDeviationSum += Math.pow(secondSample[i] - secondAverage, 2);
        }

        if (firstSquaredDeviationSum == 0 || secondSquaredDeviationSum == 0)
            return null;

        return numerator / Math.sqrt(firstSquaredDeviationSum * secondSquaredDeviationSum);

    }

    private record IndexedValue(int originalIndex, double value) { }
}
