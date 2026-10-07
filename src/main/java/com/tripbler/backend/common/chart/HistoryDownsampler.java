package com.tripbler.backend.common.chart;

import java.util.*;
import java.util.function.ToDoubleFunction;

/** 첫/마지막 값과 각 구간의 최저/최고점을 보존하는 차트 응답 축소. */
public final class HistoryDownsampler {
    private HistoryDownsampler() {}
    public static <T> List<T> reduce(List<T> source, int maximum, ToDoubleFunction<T> value) {
        if (maximum < 4) throw new IllegalArgumentException("maximum must be >= 4");
        if (source.size() <= maximum) return List.copyOf(source);
        int buckets = (maximum - 2) / 2;
        List<T> result = new ArrayList<>(maximum);
        result.add(source.get(0));
        for (int bucket = 0; bucket < buckets; bucket++) {
            int from = 1 + (int) ((long) bucket * (source.size() - 2) / buckets);
            int until = 1 + (int) ((long) (bucket + 1) * (source.size() - 2) / buckets);
            int min = from, max = from;
            for (int i = from + 1; i < until; i++) {
                if (value.applyAsDouble(source.get(i)) < value.applyAsDouble(source.get(min))) min = i;
                if (value.applyAsDouble(source.get(i)) > value.applyAsDouble(source.get(max))) max = i;
            }
            result.add(source.get(Math.min(min, max)));
            if (min != max) result.add(source.get(Math.max(min, max)));
        }
        result.add(source.get(source.size() - 1));
        return List.copyOf(result);
    }
}
