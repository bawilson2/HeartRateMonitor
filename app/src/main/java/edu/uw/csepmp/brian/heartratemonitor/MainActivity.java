package edu.uw.csepmp.brian.heartratemonitor;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;


public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    String TAG = "APP";
    CameraBridgeViewBase mOpenCvCameraView;
    DynamicSeries series;
    MyObservable dataChangeSignal;
    Integer saveDataPoints = 100;
    Integer countSinceUpdate = 0;
    Integer windowSize = 30;
    Integer updateFrequency = 20;



    // redraws a plot whenever an update is received:
    private class MyPlotUpdater implements Observer {
        Plot plot;

        public MyPlotUpdater(Plot plot) {
            this.plot = plot;
        }

        @Override
        public void update(Observable o, Object arg) {
            plot.redraw();
            if(countSinceUpdate % updateFrequency == updateFrequency -1 ) {
                series.calculateBMP();
            }
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.CvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // get handles to our View defined in layout.xml:
        XYPlot dynamicPlot = (XYPlot) findViewById(R.id.dynamicXYPlot);

        MyPlotUpdater plotUpdater = new MyPlotUpdater(dynamicPlot);

        // getInstance and position datasets:
        dataChangeSignal = new MyObservable();
        series = new DynamicSeries(new LinkedList<Double>(), "");

        LineAndPointFormatter formatter1 = new LineAndPointFormatter(
                Color.rgb(0, 0, 0), null, null, null);
        formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        formatter1.getLinePaint().setStrokeWidth(10);
        dynamicPlot.addSeries(series, formatter1);

        dataChangeSignal.addObserver(plotUpdater);


        dynamicPlot.getGraphWidget().getDomainLabelPaint().setColor(Color.TRANSPARENT);
        dynamicPlot.getGraphWidget().getRangeLabelPaint().setColor(Color.TRANSPARENT);

        // create a dash effect for domain and range grid lines:
        DashPathEffect dashFx = new DashPathEffect(
                new float[]{PixelUtils.dpToPix(3), PixelUtils.dpToPix(3)}, 0);
        dynamicPlot.getGraphWidget().getDomainGridLinePaint().setPathEffect(dashFx);
        dynamicPlot.getGraphWidget().getRangeGridLinePaint().setPathEffect(dashFx);

        dynamicPlot.getLayoutManager().remove(dynamicPlot.getDomainLabelWidget());
        dynamicPlot.getLayoutManager().remove(dynamicPlot.getLegendWidget());

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, mLoaderCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is invoked when camera preview has started. After this method is invoked
     * the frames will start to be delivered to client via the onCameraFrame() callback.
     *
     * @param width  -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    /**
     * This method is invoked when camera preview has been stopped for some reason.
     * No frames will be delivered via onCameraFrame() callback after this method is called.
     */
    @Override
    public void onCameraViewStopped() {

    }


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currentFrame = inputFrame.rgba();
        final Scalar scalar = Core.mean(currentFrame);

        Log.d(TAG, "Red  " + scalar.val[0]);
        series.addValue(scalar.val[0]);
        dataChangeSignal.notifyObservers();
        countSinceUpdate++;
        if(countSinceUpdate > updateFrequency) {
            countSinceUpdate = 0;
        }



        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Double BPM = series.getMedianBPM();
                TextView bpmTextView = (TextView) findViewById(R.id.beatsView);
                bpmTextView.setText(Double.toString(Math.round(BPM)) + " Beats Per Minute");
            }
        });

        return currentFrame;
    }

    class MyObservable extends Observable {
        @Override
        public void notifyObservers() {
            setChanged();
            super.notifyObservers();
        }
    }

    class DynamicSeries implements XYSeries {
        private LinkedList<SeriesNode> data;
        private LinkedList<Double> bpmFilter = new LinkedList<>();
        private String title;
        private static final int MEDIAN_FILTER_SIZE = 5;
        private static final int SMOOTHING_RATE = 3;

        public DynamicSeries(LinkedList inputData, String title) {
            this.data = inputData;
            this.title = title;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public Number getX(int index) {
            return index;
        }

        @Override
        public Number getY(int index) {
            return data.get(index).getValue();
        }


        public void addValue(Double newValue) {
            if (data.size() == saveDataPoints) {
                data.pop();
            }
            //do a little smoothing on the previous data
            if(data.size() > SMOOTHING_RATE) {
                Iterator<SeriesNode> nodeIter = data.descendingIterator();
                Double[] lastThree = new Double[SMOOTHING_RATE];
                lastThree[0] = nodeIter.next().getValue();
                lastThree[1] = nodeIter.next().getValue();
                lastThree[2] = newValue;
                Arrays.sort(lastThree);
                data.peekLast().setValue(lastThree[1]);
            }
            data.add(new SeriesNode(newValue));
        }


        public Double getMedian(SeriesNode[] dataArray) {
            Arrays.sort(dataArray);
            return ((SeriesNode)dataArray[dataArray.length / 2]).getValue();
        }

        public void calculateBMP() {
            LinkedList<SeriesNode> clonedData = new LinkedList<>(data);
            while(clonedData.size() > windowSize) {
                clonedData.removeLast();
            }

            SeriesNode first = clonedData.peekFirst();
            SeriesNode last = clonedData.peekLast();
            Long range = last.getTimestamp() - first.getTimestamp();

            Double median = getMedian(clonedData.toArray(new SeriesNode[]{}));
            int beats = 0;
            for(int i =0; i<clonedData.size()-1; i++) {
                if(clonedData.get(i).getValue() > median && clonedData.get(i+1).getValue() <= median) {
                    beats++;
                }
            }

            if (bpmFilter.size() == MEDIAN_FILTER_SIZE) {
                bpmFilter.pop();
            }
            bpmFilter.add(beats * 60000D / range);
        }

        public Double getMedianBPM() {
            Double filterBPM = 0D;
            Double[] bpmArray = bpmFilter.toArray(new Double[]{});

            Arrays.sort(bpmArray);
            if(bpmArray.length == MEDIAN_FILTER_SIZE) {
                filterBPM = bpmArray[1];
            }
            return filterBPM;
        }

        class SeriesNode extends Number implements Comparable<SeriesNode>{
            private Double value;
            private Long timestamp;

            public SeriesNode(Double val) {
                value = val;
                timestamp = System.currentTimeMillis();
            }

            public Long getTimestamp() {
                return timestamp;
            }

            public Double getValue() {
                return value;
            }

            public void setValue(Double value) {
                this.value = value;
            }

            @Override
            public double doubleValue() {
                return value;
            }

            @Override
            public float floatValue() {
                return value.floatValue();
            }

            @Override
            public int intValue() {
                return value.intValue();
            }

            @Override
            public long longValue() {
                return value.longValue();
            }

            @Override
            public int compareTo(SeriesNode another) {
                return this.getValue().compareTo(another.getValue());
            }
        }
    }




}
