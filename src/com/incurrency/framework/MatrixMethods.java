/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;
import org.jblas.DoubleMatrix;
import org.jblas.ranges.IntervalRange;

/**
 *
 * @author Pankaj
 */
public class MatrixMethods {

    private static final Logger logger = Logger.getLogger(MatrixMethods.class.getName());
    public static String newline = System.getProperty("line.separator");

    /**
     * Inserts a new column at range location.
     *
     * @param m
     * @param values
     * @param range
     * @return
     */
    public static DoubleMatrix insertColumn(DoubleMatrix m, double[] values, int[] range) {
        int newColumns = range[1] - range[0] + 1;
        DoubleMatrix m1 = m.getRange(0, m.rows, 0, range[0]);
        DoubleMatrix m2 = new DoubleMatrix(m.rows, newColumns, values);
        DoubleMatrix out = DoubleMatrix.concatHorizontally(m1, m2);
        if (range[0] < m.columns) {
            DoubleMatrix m3 = m.getRange(0, m.rows, range[0], m.columns);
            out = DoubleMatrix.concatHorizontally(out, m3);
        }
        return out;
    }

    public static DoubleMatrix getSubSetVector(DoubleMatrix m, int[] subsetIndices) {
        DoubleMatrix out = m.get(subsetIndices);
        return out.reshape(1, out.length);
    }

    public static int[] getValidIndices(DoubleMatrix... matrices) {
        int[] indices = new int[0];
        for (DoubleMatrix m : matrices) {
            indices = Utilities.addArraysNoDuplicates(indices, m.ne(ReservedValues.EMPTY).findIndices());
        }
        return indices;
    }

    public static double lValue(DoubleMatrix m) {
        int length = m.length;
        return m.data[length - 1];
    }

    public static DoubleMatrix range(DoubleMatrix m, int range, double step) {
        DoubleMatrix out = DoubleMatrix.zeros(m.length);
        DoubleMatrix hhv = hhv(m, range);
        DoubleMatrix llv = llv(m, range);
        int[] naValues = hhv.eq(ReservedValues.EMPTY).findIndices();
        out = (m.sub(llv)).div(hhv.sub(llv));
        out.put(naValues, ReservedValues.EMPTY);
        out = roundTo(out, step);
        return out;
    }

    public static DoubleMatrix roundTo(DoubleMatrix m, double step) {
        DoubleMatrix out = DoubleMatrix.zeros(m.length);
        for (int i = 0; i < m.length; i++) {
            out.put(i, Utilities.roundTo(m.get(i), 0.2));
        }
        return out;

    }

    /**
     * Inserts a new timeseries using target barsize.CompressionFactor provides
     * the link between source and target barsize.For example,compression factor
     * between daily and weekly will be 5.This method does not correctly account
     * for intervening holidays and is therefore unusable for compression when
     * source is DAILY or greater.
     *
     * @param s
     * @param source
     * @param target
     * @param compressionFactor
     */
    public static void compressBars(BeanSymbol s, EnumBarSize source, EnumBarSize target, String[] compress, int compressionFactor, String compressionRule) {
        DoubleMatrix mCompress = new DoubleMatrix();
        ArrayList<Integer> indicesCompress = new ArrayList<>();
        for (int i = 0; i < compress.length; i++) {
            int temp = s.getRowLabels().get(source).indexOf(compress[i]);
            if (temp == -1) {
                return;//exit without doing anything
            } else {
                indicesCompress.add(temp);
            }
        }

        if (indicesCompress.size() > 0) {
            mCompress = s.getTimeSeries().get(source).getRows(Utilities.convertIntegerListToArray(indicesCompress));

        }

        //create row cutoffs. A cutoff determines the starting index level for each compressed bar
        int sourceBarCount = s.getColumnLabels().get(source).size();
        ArrayList<Integer> barCutOffs = new ArrayList<>();
        int stub = 0;//there might be stubs in the intraday bars/or EOD bars might not be starting on Mondays..

        if (compressionFactor > 0) {//intraday
            barCutOffs.add(0); //start from the beginning of the array
            for (int i = 1; i < sourceBarCount; i++) {
                if ((i - stub) % (compressionFactor) - 1 == 0
                        && Utilities.timeStampsWithinDay(s.getColumnLabels().get(source).get(i), s.getColumnLabels().get(source).get(i - 1), Algorithm.timeZone)) {
                    barCutOffs.add(i);
                } else {
                    if (!barCutOffs.contains(Integer.valueOf(1 - 1))) {
                        barCutOffs.add(i - 1);
                        if (stub == 0) {
                            stub = i - 1;//add a value to stub only if i-1 bar was not in barCutOffs list. This means we had a premature EOD on intra-day bars
                        }
                    }
                }
            }
        } else {
            barCutOffs = getIndices(s.getColumnLabels().get(source), compressionRule, Algorithm.timeZone);
        }

        if (mCompress.length > 0) {
            for (int i = 1; i <= barCutOffs.size(); i++) {
                int startIndex = barCutOffs.get(i - 1);
                int endIndex = i >= barCutOffs.size() ? s.getColumnLabels().get(source).size() : barCutOffs.get(i);

                IntervalRange r = new IntervalRange(startIndex, endIndex);
                DoubleMatrix m = mCompress.getColumns(Utilities.range(startIndex, 1, endIndex - startIndex));
                double[] prices = new double[indicesCompress.size()];
                for (int j = 0; j < prices.length; j++) {
                    int rowIndex = indicesCompress.get(j);
                    String label = s.getRowLabels().get(source).get(rowIndex);
                    int[] tradedBars = m.getRow(rowIndex).ne(ReservedValues.EMPTY).findIndices();
                    if (tradedBars.length == 0) {
                        prices[j] = ReservedValues.EMPTY;
                    } else {
                        switch (label) {
                            case "open":
                                prices[j] = m.getRow(rowIndex).get(0, tradedBars[0]);
                                break;
                            case "high":
                                prices[j] = Doubles.max(m.getRow(rowIndex).get(tradedBars).data);
                                break;
                            case "low":
                                prices[j] = Doubles.min(m.getRow(rowIndex).get(tradedBars).data);
                                break;
                            case "close":
                            case "settle":
                                prices[j] = m.getRow(rowIndex).get(0, tradedBars[tradedBars.length - 1]);
                                break;
                            case "volume":
                                prices[j] = m.getRow(rowIndex).rowSums().data[0];
                                break;
                            default:
                                break;
                        }
                    }
                }
                long time = s.getColumnLabels().get(source).get(startIndex);
                s.setTimeSeries(target, time, compress, prices);
            }
        }

    }

    private static ArrayList<Integer> getIndices(List<Long> time, String rule, String timeZone) {
        ArrayList<Integer> out = new ArrayList<>();
        out.add(0);
        switch (rule) {
            case "BOW": //beginning of week
                for (int i = 1; i < time.size(); i++) {
                    Calendar calnow = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    Calendar calprior = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    calnow.setTimeInMillis(time.get(i));
                    calprior.setTimeInMillis(time.get(i - 1));
                    if (calnow.get(Calendar.DAY_OF_WEEK) >= Calendar.MONDAY && calprior.get(Calendar.DAY_OF_WEEK) >= calnow.get(Calendar.DAY_OF_WEEK)) {
                        out.add(i);
                    }
                }
                break;
            case "BOM": //Beginning of Month
                for (int i = 1; i < time.size(); i++) {
                    Calendar calnow = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    Calendar calprior = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    calnow.setTimeInMillis(time.get(i));
                    calprior.setTimeInMillis(time.get(i - 1));
                    if (calnow.get(Calendar.DAY_OF_MONTH) < calprior.get(Calendar.DAY_OF_MONTH)) {
                        out.add(i);
                    }
                }
                break;
            case "BOY"://Beginning of year
                for (int i = 1; i < time.size(); i++) {
                    Calendar calnow = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    Calendar calprior = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    calnow.setTimeInMillis(time.get(i));
                    calprior.setTimeInMillis(time.get(i - 1));
                    if (calnow.get(Calendar.DAY_OF_YEAR) < calprior.get(Calendar.DAY_OF_YEAR)) {
                        out.add(i);
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    /**
     * Returns 1 when Matrix a1 cross above Matrix a2
     *
     * @param a
     * @param b
     * @return
     */
    public static DoubleMatrix cross(DoubleMatrix a1, DoubleMatrix a2) {
        DoubleMatrix prior_a1 = ref(a1, -1);
        return prior_a1.lt(ref(a2, -1)).and(a1.gt(a2));
    }

    /**
     * Returns a matrix shifted by the integer value provided in shift.If shift
     * is -negative, the return matrix moves earlier matrix values forward.
     *
     * @param m
     * @param shift
     * @return
     */
    public static DoubleMatrix ref(DoubleMatrix m, int shift) {
        DoubleMatrix out = DoubleMatrix.zeros(m.getRows(), m.getColumns());
        if (shift < 0) {//
            shift = -shift;
            int[] indicesSource = Ints.toArray(ContiguousSet.create(Range.closed(0, m.columns - 1 - shift), DiscreteDomain.integers()));
            int[] indicesTarget = Ints.toArray(ContiguousSet.create(Range.closed(shift, m.columns - 1), DiscreteDomain.integers()));
            out.put(indicesTarget, m.getColumns(indicesSource));
        } else {
            shift = shift;
            int[] indicesSource = Ints.toArray(ContiguousSet.create(Range.closed(shift, m.columns - 1), DiscreteDomain.integers()));
            int[] indicesTarget = Ints.toArray(ContiguousSet.create(Range.closed(0, m.columns - 1 - shift), DiscreteDomain.integers()));
            out.put(indicesTarget, m.getColumns(indicesSource));
        }
        return out;
    }

    public static DoubleMatrix ref(DoubleMatrix m, DoubleMatrix mshift) {
        DoubleMatrix out = DoubleMatrix.zeros(m.getRows(), m.getColumns());
        for (int i = 0; i < m.length; i++) {
            int shift = (int) mshift.get(i);
            if (shift < 0) {//
                shift = -shift;
                //int[] indicesSource = Ints.toArray(ContiguousSet.create(Range.closed(0, m.columns - 1 - shift), DiscreteDomain.integers()));
                //int[] indicesTarget = Ints.toArray(ContiguousSet.create(Range.closed(shift, m.columns - 1), DiscreteDomain.integers()));
                //out.put(indicesTarget, m.getColumns(indicesSource));
                if (i >= shift) {
                    out.put(i, m.get(i - shift));
                }
            } else {

            }

        }
        return out;
    }

    public static DoubleMatrix shift(DoubleMatrix m1, DoubleMatrix m2, int ref) {
        DoubleMatrix out = DoubleMatrix.zeros(m1.getRows(), m1.getColumns());
        int nSize = m1.length;
        for (int i = 1; i < nSize; i++) {
            boolean trigger = m2.get(i) != m2.get(i - 1) && m2.get(i) == ref;
            if (m1.get(i) != m1.get(i - 1) | trigger) {
                out.put(i, m1.get(i - 1));
            } else {
                out.put(i, out.get(i - 1));
            }
        }

        return out;
    }

    /**
     * Calculates the number of bars (time periods) that have passed since
     * Matrix m was true (or 1)
     *
     * @param m
     * @return
     */
    public static DoubleMatrix barsSince(DoubleMatrix m) {
        DoubleMatrix out = DoubleMatrix.ones(1, m.length);
        int[] indices_truebars = m.findIndices();
        out.put(indices_truebars, 0);
        double[] out_values = out.data;
        for (int i = 1; i < out.length; i++) {
            if (out_values[i] == 1) {
                out_values[i] = out_values[i - 1] + 1;
            }
        }
        out = new DoubleMatrix(1, m.length, out_values);
        return out;
    }

    /**
     * Returns the value of M when EXPRESSION was true on the INDEX -th most
     * recent occurrence.Note: this function does not allow negative values for
     * n.
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix valueWhen(DoubleMatrix expression, DoubleMatrix m, int index) {
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        if (index > 0) {
            int[] indices = expression.findIndices(); //get non zero values
            DoubleMatrix values = m.get(indices).reshape(1, indices.length);
            int[] newindices = Arrays.copyOfRange(indices, index - 1, indices.length);
            DoubleMatrix newValues = values.get(new IntervalRange(0, 1), new IntervalRange(0, values.columns - index + 1));
            out.put(newindices, newValues);
            double paste = 0;
            for (int i = 0; i < out.length; i++) {
                if (out.get(i) == 0) {
                    out.put(i, paste);
                } else {
                    paste = out.get(i);
                }
            }
        } else {//forward looking
            int[] indices = expression.findIndices(); //get non zero values
            DoubleMatrix values = m.get(indices).reshape(1, indices.length);
            int[] newindices = Arrays.copyOfRange(indices, index, indices.length - index - 1);
            DoubleMatrix newValues = values.get(new IntervalRange(0, 1), new IntervalRange(index + 1, values.length));
            out.put(newindices, newValues);
            double paste = 0;
            for (int i = 0; i < out.length; i++) {
                if (out.get(i) == 0) {
                    out.put(i, paste);
                } else {
                    paste = out.get(i);
                }
            }

        }
        return out;

    }

    /**
     * Returns the highest value of M since the EXPRESSION was true, on the
     * INDEX-th most recent occurrence.
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix HighestvalueWhen(DoubleMatrix expression, DoubleMatrix m, int index) {
        /*
         * Algorithm
         * Step 1: get indices of true values in expression
         * Step 2: Get values of corresponding values in target matrix "m"
         * Step 3: Take subset of indices and values based on index value.
         * Step 4: update values in the out matrix at the index values
         * Step 5: Loop through the out vector
         *          if value=0, 
         *              if(paste>0)
         *              insert into out (across prior indices )the value of paste
         *              paste=0
         *              clear index
         *              endif
         *              insert into current out(i) the previous out i
         *          else if value>0
         *              save index number
         *              save paste=max(value,paste);
         *          endif
         * Step 6: Cleanup. if there is any value in paste, insert to out vector
         */

        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        int[] indices = expression.findIndices(); //(Step 1)
        DoubleMatrix values = m.get(indices).reshape(1, indices.length); //(Step 2)
        int[] newindices = Arrays.copyOfRange(indices, index - 1, indices.length); //Step 3
        DoubleMatrix newValues = values.get(new IntervalRange(0, 1), new IntervalRange(0, values.columns - index + 1));
        out.put(newindices, newValues);//Step 4
        double paste = 0;
        ArrayList<Double> tempValues = new ArrayList<>();
        ArrayList<Integer> tempIndices = new ArrayList<>();
        for (int i = 0; i < out.length; i++) { //Step 5
            if (out.get(i) == 0) {
                if (paste > 0) {
                    int[] aIndices = Ints.toArray(tempIndices);
                    out.put(aIndices, paste);

                    paste = 0;
                    tempIndices.clear();
                }
                if (i > 0) {
                    out.put(i, out.get(i - 1));
                }
            } else {
                tempIndices.add(i);
                paste = Math.max(paste, out.get(i));
            }
        }
        //Step 6
        if (paste > 0) {
            int[] aIndices = Ints.toArray(tempIndices);
            //DoubleMatrix tm = new DoubleMatrix(1, tempIndices.size());
            //tm.put(1, aIndices, paste);                
            out.put(aIndices, paste);
            paste = 0;
            tempIndices.clear();
        }
        return out;
    }

    /**
     * Returns the lowest value of M, since the EXPRESSION was true on the
     * INDEX-th most recent occurrence.
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix LowestvalueWhen(DoubleMatrix expression, DoubleMatrix m, int index) {
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        int[] indices = expression.findIndices(); //(Step 1)
        DoubleMatrix values = m.get(indices).reshape(1, indices.length); //(Step 2)
        int[] newindices = Arrays.copyOfRange(indices, index - 1, indices.length); //Step 3
        DoubleMatrix newValues = values.get(new IntervalRange(0, 1), new IntervalRange(0, values.columns - index + 1));
        out.put(newindices, newValues);//Step 4
        double paste = 0;
        ArrayList<Double> tempValues = new ArrayList<>();
        ArrayList<Integer> tempIndices = new ArrayList<>();
        for (int i = 0; i < out.length; i++) { //Step 5
            if (out.get(i) == 0) {
                if (paste > 0) {
                    int[] aIndices = Ints.toArray(tempIndices);
                    out.put(aIndices, paste);
                    paste = 0;
                    tempIndices.clear();
                }
                if (i > 0) {
                    out.put(i, out.get(i - 1));
                }
            } else {
                tempIndices.add(i);
                paste = Math.min(paste, out.get(i));
            }
        }
        //Step 6
        if (paste > 0) {
            int[] aIndices = Ints.toArray(tempIndices);
            out.put(1, aIndices, paste);
            paste = 0;
            tempIndices.clear();
        }
        return out;
    }

    /**
     * Returns the highest value of M when the EXPRESSION was true, on the
     * INDEX-th most recent occurrence.Other values are set to zero.
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix HighestvalueSignalWhen(DoubleMatrix expression, DoubleMatrix m, int index) {
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        DoubleMatrix outsignal = DoubleMatrix.zeros(1, m.columns);
        int[] indices = expression.findIndices(); //(Step 1)
        DoubleMatrix values = m.get(indices).reshape(1, indices.length); //(Step 2)
        if (index <= indices.length + 1) {
            int[] newindices = Arrays.copyOfRange(indices, index - 1, indices.length); //Step 3
            DoubleMatrix newValues = values.get(new IntervalRange(0, 1), new IntervalRange(0, values.columns - index + 1));
            out.put(newindices, newValues);//Step 4.Created a matrix containing required values of m.
            double paste = 0;
            int tempIndices = 0;
            for (int i = 0; i < out.length; i++) { //Step 5
                if (out.get(i) == 0) {
                    if (paste > 0) {
                        outsignal.put(tempIndices, paste);
                        paste = 0;
                        tempIndices = 0;
                    }
                } else {
                    if (tempIndices == 0) {
                        tempIndices = i;
                    }
                    paste = Math.max(paste, out.get(i));
                }
            }
            //Step 6
            if (paste > 0) {
                outsignal.put(tempIndices, paste);
                paste = 0;
                tempIndices = 0;

            }

        }
        return outsignal;
    }

    /**
     * Returns the highest value of matrix m, since "expression" was true on the
     * "index"th most recent occurrence
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix highestSince(DoubleMatrix expression, DoubleMatrix m, int index) {
        m = m.reshape(1, m.length);
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        int[] indices = expression.findIndices(); //(Step 1)
        //int startindex = 0;
        int endindex = indices[0];
        int terminateindex = indices[0];
        double hhv = 0;
        if (index > 0 && index <= indices.length + 1) {
            for (int i = 0; i < indices.length; i++) {
                //startindex = endindex;
                endindex = indices[-1 + i + index];
                if (i + index < indices.length) {
                    terminateindex = indices[i + index]; //next index after endindex
                } else {
                    terminateindex = m.length - 1;
                }
                for (int j = endindex; j <= terminateindex; j++) { //get hhv excluding value of terminate index
                    if (j >= endindex) {
                        hhv = m.get(0, new IntervalRange(endindex, j + 1)).max();
                        out.put(j, hhv);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Returns the lowest value of matrix m, since "expression" was true on the
     * "index"th most recent occurrence
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix lowestSince(DoubleMatrix expression, DoubleMatrix m, int index) {
        m = m.reshape(1, m.length);
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        int[] indices = expression.findIndices(); //(Step 1)
        //int startindex = 0;
        int endindex = indices[0];
        int terminateindex = indices[0];
        double llv = 0;
        if (index > 0 && index <= indices.length + 1) {
            for (int i = 0; i < indices.length; i++) {
                //startindex = endindex;
                endindex = indices[-1 + i + index];
                if (i + index < indices.length) {
                    terminateindex = indices[i + index]; //next index after endindex
                } else {
                    terminateindex = m.length - 1;
                }
                for (int j = endindex; j <= terminateindex; j++) { //get hhv excluding value of terminate index
                    if (j >= endindex) {
                        llv = m.get(0, new IntervalRange(endindex, j + 1)).min();
                        out.put(j, llv);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Calculates a cumulative sum of the matrix m from the first period .
     *
     * @param m
     * @return
     */
    public static DoubleMatrix cum(DoubleMatrix m) {
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        for (int i = 1; i <= m.length; i++) {
            int[] range = Utilities.range(0, 1, i);
            out.put(i-1, m.get(range).sum());
        }
        return out;
    }

    /**
     * Creates a doublematrix of 1 row and specified columns, filled with value
     * of i
     *
     * @param i
     * @param size
     * @return
     */
    public static DoubleMatrix create(Double value, int columns) {
        DoubleMatrix out = DoubleMatrix.ones(1, columns);
        return out.mul(value);
    }

    /**
     * removes excessive signals:returns 1 on the first occurence of "true"
     * signal in a1,then returns 0 until a2 is true even if there are "true"
     * signals in a1
     *
     * @param a1
     * @param a2
     * @return
     */
    public static DoubleMatrix exrem(DoubleMatrix a1, DoubleMatrix a2) {
        DoubleMatrix out = DoubleMatrix.zeros(1, a1.columns);
        if (a1.length == a2.length) {
            DoubleMatrix a1_ones = a1.ne(0);
            DoubleMatrix a2_negones = a2.ne(0).mul(-1);
            DoubleMatrix a = a1_ones.add(a2_negones);
            DoubleMatrix flipflop = highestSince(a2_negones.eq(-1), a1, 1);
            DoubleMatrix flipflop_1 = ref(flipflop, -1);
            out = flipflop.eq(1).and(flipflop_1.eq(0));
        }
        return out;
    }

    /**
     * Returns the lowest value of M when the EXPRESSION was true, on the
     * INDEX-th most recent occurrence.Other values are set to zero.
     *
     * @param expression
     * @param m
     * @param index
     * @return
     */
    public static DoubleMatrix LowestvalueSignalWhen(DoubleMatrix expression, DoubleMatrix m, int index) {
        DoubleMatrix out = DoubleMatrix.zeros(1, m.columns);
        DoubleMatrix outsignal = DoubleMatrix.zeros(1, m.columns);
        int[] indices = expression.findIndices(); //(Step 1)
        DoubleMatrix values = m.get(indices).reshape(1, indices.length); //(Step 2)
        if (index <= indices.length + 1) {
            int[] newindices = Arrays.copyOfRange(indices, index - 1, indices.length); //Step 3
            DoubleMatrix newValues = values.get(new IntervalRange(0, 1), new IntervalRange(0, values.columns - index + 1));
            out.put(newindices, newValues);//Step 4
            double paste = Double.MAX_VALUE;
            int tempIndices = 0;
            for (int i = 0; i < out.length; i++) { //Step 5
                if (out.get(i) == 0) {
                    if (paste > 0) {
                        outsignal.put(tempIndices, paste);
                        paste = Double.MAX_VALUE;
                        tempIndices = 0;
                    }
                } else {
                    if (tempIndices == 0) {
                        tempIndices = i;
                    }
                    paste = Math.min(paste, out.get(i));
                }
            }
            //Step 6
            if (paste != Double.MAX_VALUE) {
                outsignal.put(tempIndices, paste);
                paste = 0;
                tempIndices = 0;

            }

        }
        return outsignal;
    }

    /**
     * Returns a native array containing the highest high from SOURCE array, for
     * a specified RANGE
     *
     * @param source
     * @param range
     * @return
     */
    public static DoubleMatrix hhv(DoubleMatrix m, int range) {
        DoubleMatrix out = MatrixMethods.create(ReservedValues.EMPTY, m.length);
        //double[] out = new double[m.length - range + 1];
        double[] source = m.data;

        for (int i = 0; i < out.length; i++) {
            if (i >= -range - 1) {
                if (range > 0) {
                    out.put(i, Doubles.max(Arrays.copyOfRange(source, i, i + range + 1)));
                } else {
                    out.put(i, Doubles.max(Arrays.copyOfRange(source, i + range + 1, i + 1)));
                }
            }
        }

        return out;
    }

    /**
     * Returns a native array containing the lowest low from SOURCE array, for a
     * specified RANGE
     *
     * @param source
     * @param range
     * @return
     */
    public static DoubleMatrix llv(DoubleMatrix m, int range) {
        DoubleMatrix out = MatrixMethods.create(ReservedValues.EMPTY, m.length);
        //double[] out = new double[m.length - range + 1];
        double[] source = m.data;

        for (int i = 0; i < out.length; i++) {
            if (i >= -range - 1) {
                if (range > 0) {
                    out.put(i, Doubles.min(Arrays.copyOfRange(source, i, i + range + 1)));
                } else {
                    out.put(i, Doubles.min(Arrays.copyOfRange(source, i + range + 1, i + 1)));
                }
            }
        }
        return out;
    }

}
