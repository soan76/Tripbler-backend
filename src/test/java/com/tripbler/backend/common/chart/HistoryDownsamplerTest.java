package com.tripbler.backend.common.chart;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HistoryDownsamplerTest {
    record Point(int index, double value) {}
    @Test void preservesEndpointsExtremaOrderAndBound() {
        var points = new ArrayList<Point>();
        for (int i = 0; i < 10000; i++) points.add(new Point(i, i == 1234 ? -100000 : i == 5678 ? 100000 : Math.sin(i)));
        var result = HistoryDownsampler.reduce(points, 300, Point::value);
        assertTrue(result.size() <= 300);
        assertEquals(points.get(0), result.get(0));
        assertEquals(points.get(9999), result.get(result.size() - 1));
        assertTrue(result.contains(points.get(1234)));
        assertTrue(result.contains(points.get(5678)));
        for (int i = 1; i < result.size(); i++) assertTrue(result.get(i).index() > result.get(i - 1).index());
        assertEquals(points.subList(0, 3), HistoryDownsampler.reduce(points.subList(0, 3), 300, Point::value));
    }
}
