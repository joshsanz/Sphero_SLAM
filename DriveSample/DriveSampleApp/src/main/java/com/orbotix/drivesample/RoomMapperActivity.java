package com.orbotix.drivesample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.orbotix.ConvenienceRobot;
import com.orbotix.Sphero;
import com.orbotix.async.CollisionDetectedAsyncData;
import com.orbotix.async.DeviceSensorAsyncMessage;
import com.orbotix.classic.DiscoveryAgentClassic;
import com.orbotix.common.DiscoveryAgent;
import com.orbotix.common.DiscoveryAgentEventListener;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.internal.AsyncMessage;
import com.orbotix.common.internal.DeviceResponse;
import com.orbotix.common.sensor.SensorFlag;
import com.orbotix.subsystem.SensorControl;

import java.util.List;

public class RoomMapperActivity extends Activity implements DiscoveryAgentEventListener,
                                                            RobotChangedStateListener {

    private static final String TAG = "RoomMapperActivity";

    private DiscoveryAgent _currentDiscoveryAgent;
    private ConvenienceRobot _connectedRobot;
    private static long _sensorFlags = SensorFlag.ACCELEROMETER_NORMALIZED.longValue()
            | SensorFlag.GYRO_NORMALIZED.longValue()
            | SensorFlag.VELOCITY.longValue()
            | SensorFlag.LOCATOR.longValue();

    private double _posX;
    private double _posY;
    private double _posZ;
    private double _zDot;
    private double _yaw;
    private double _pitch;

    private double _accelX;
    private double _accelY;
    private double _accelZ;
    private double _gyroX;
    private double _gyroY;
    private double _gyroZ;
    private double _locX;
    private double _locY;
    private double _velX;
    private double _velY;

    private GraphView _graph;
    private LineGraphSeries<DataPoint> _lSeries;
    private PointsGraphSeries<DataPoint> _pSeries;

    private TextView _logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_mapper);

        _graph = (GraphView) findViewById(R.id.graph);
        _logTextView = (TextView) findViewById(R.id.logTextView);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Start discovery of any spheros in the area
        _currentDiscoveryAgent = DiscoveryAgentClassic.getInstance();
        startDiscovery();

//        // Set up a sample graph
        _lSeries = new LineGraphSeries<>(new DataPoint[] {});
//                new DataPoint(0, 1),
//                new DataPoint(1, 5),
//                new DataPoint(2, 3),
//                new DataPoint(3, 2),
//                new DataPoint(4, 6),
//                new DataPoint(3, 4),
//                new DataPoint(1, 3)
//        });
//
        _pSeries = new PointsGraphSeries<>(new DataPoint[] {});
//                new DataPoint(0, 1),
//                new DataPoint(1, 5),
//                new DataPoint(2, 3),
//                new DataPoint(3, 2),
//                new DataPoint(4, 6),
//                new DataPoint(3, 4),
//                new DataPoint(1, 3)
//        });

        _graph.addSeries(_lSeries);
        _graph.addSeries(_pSeries);
        _pSeries.setShape(PointsGraphSeries.Shape.TRIANGLE);
        _graph.getViewport().setXAxisBoundsManual(true);
        _graph.getViewport().setMinX(0);
        _graph.getViewport().setMaxX(5);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (_currentDiscoveryAgent != null) {
            _currentDiscoveryAgent.removeRobotStateListener(this);
            for (Robot r : _currentDiscoveryAgent.getConnectedRobots()) {
                r.sleep();
            }
        }
    }
    @Override
    protected void onStop() {
        if (_currentDiscoveryAgent != null) {
            _currentDiscoveryAgent.removeRobotStateListener(this);
            for (Robot r : _currentDiscoveryAgent.getConnectedRobots()) {
                r.sleep();
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DiscoveryAgentClassic.getInstance().addRobotStateListener(null);
    }

    /**
     * Starts discovery on the set discovery agent and look for robots
     */
    private void startDiscovery() {
        try {
            // You first need to set up so that the discovery agent will notify you when it finds robots.
            // To do this, you need to implement the DiscoveryAgentEventListener interface (or declare
            // it anonymously) and then register it on the discovery agent with DiscoveryAgent#addDiscoveryListener()
            _currentDiscoveryAgent.addDiscoveryListener(this);
            // Second, you need to make sure that you are notified when a robot changes state. To do this,
            // implement RobotChangedStateListener (or declare it anonymously) and use
            // DiscoveryAgent#addRobotStateListener()
            _currentDiscoveryAgent.addRobotStateListener(this);
            // Then to start looking for a Sphero, you use DiscoveryAgent#startDiscovery()
            // You do need to handle the discovery exception. This can occur in cases where the user has
            // Bluetooth off, or when the discovery cannot be started for some other reason.
            _currentDiscoveryAgent.startDiscovery(this);
        } catch (DiscoveryException e) {
            Log.e(TAG, "Could not start discovery. Reason: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Invoked when the discovery agent finds a new available robot, or updates and already available robot
     * @param robots The list of all robots, connected or not, known to the discovery agent currently
     */
    @Override
    public void handleRobotsAvailable(List<Robot> robots) {
        _currentDiscoveryAgent.connect(robots.get(0));

        _logTextView.append("Identifier: " + robots.get(0).getIdentifier() + "\n");
    }

    /**
     * Invoked when a robot changes state. For example, when a robot connects or disconnects.
     * @param robot The robot whose state changed
     * @param type Describes what changed in the state
     */
    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
        // For the purpose of this sample, we'll only handle the connected and disconnected notifications
        switch (type) {
            // A robot was connected, and is ready for you to send commands to it.
            case Online:
                _currentDiscoveryAgent.stopDiscovery();
                _currentDiscoveryAgent.removeDiscoveryListener(this);
                _connectedRobot = new Sphero(robot);
                _logTextView.append("Name: " + _connectedRobot.getRobot().getName() + "\n");

                // For visual feedback let's turn the robot green saying that it's been connected
                _connectedRobot.setLed(.5f, .7f, .5f);

                // Enable sensors and register a listener for messages
                _connectedRobot.enableCollisions(true);
                _connectedRobot.enableSensors(_sensorFlags, SensorControl.StreamingRate.STREAMING_RATE10);
                _connectedRobot.addResponseListener(new ResponseListener() {
                    @Override
                    public void handleAsyncMessage(AsyncMessage asyncMessage, Robot robot) {
                        if (asyncMessage instanceof DeviceSensorAsyncMessage) {
                            DeviceSensorAsyncMessage message = (DeviceSensorAsyncMessage) asyncMessage;
                            if( message.getAsyncData() == null
                                    || message.getAsyncData().isEmpty()
                                    || message.getAsyncData().get( 0 ) == null )
                                return;
                            _accelX = message.getAsyncData().get(0).getAccelerometerData().getFilteredAcceleration().x;
                            _accelY = message.getAsyncData().get(0).getAccelerometerData().getFilteredAcceleration().y;
                            _accelZ = message.getAsyncData().get(0).getAccelerometerData().getFilteredAcceleration().z;

                            _gyroX = message.getAsyncData().get(0).getGyroData().getRotationRateFiltered().x;
                            _gyroY = message.getAsyncData().get(0).getGyroData().getRotationRateFiltered().y;
                            _gyroZ = message.getAsyncData().get(0).getGyroData().getRotationRateFiltered().z;

                            _locX = message.getAsyncData().get(0).getLocatorData().getPositionX();
                            _locY = message.getAsyncData().get(0).getLocatorData().getPositionY();
                            _velX = message.getAsyncData().get(0).getLocatorData().getVelocity().x;
                            _velY = message.getAsyncData().get(0).getLocatorData().getVelocity().y;
                            updateTrack();
                        } else if (asyncMessage instanceof CollisionDetectedAsyncData) {
                            addLandmark();
                            setNewHeading();
                        }
                    }

                    @Override
                    public void handleResponse(DeviceResponse deviceResponse, Robot robot) {
                    }
                    @Override
                    public void handleStringResponse(String s, Robot robot) {
                    }
                });

                _connectedRobot.drive(0.0f, 0.1f);
                break;
            case Disconnected:
                startDiscovery();
                break;
            default:
                Log.v(TAG, "Not handling state change notification: " + type);
                break;
        }
    }

    private void updateTrack() {
        // Basic implementation, no filtering
        _posX = _locX;
        _posY = _locY;
        _posZ = _posZ + _zDot * 0.1;
        _zDot = _zDot + _accelZ * 0.1;

        _yaw = Math.atan2(_velY, _velX);
        _pitch = _pitch + _gyroZ * 0.1;

        _lSeries.appendData(new DataPoint(_posX, _posY), true, 10000);
        _logTextView.append("Update: Position: (" + _posX + ", " + _posY + "), Heading: " + (_yaw * 180 / Math.PI) + "\n");
    }

    private void addLandmark() {
        _pSeries.appendData(new DataPoint(_posX, _posY), true, 100);
    }

    private void setNewHeading() {
        float newHeading = (float) (_yaw + (Math.random() * 170.0 + 95) * 180 / Math.PI);
        if (Math.abs(newHeading) > Math.PI) {
            newHeading *= Math.signum(newHeading);
        }
        _connectedRobot.drive(newHeading, 0.1f);
    }
}
