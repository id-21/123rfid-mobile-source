package com.zebra.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.zebra.ASCII_SDK.Command_Log;
import com.zebra.ASCII_SDK.Response_Status;
import com.zebra.demo.rfidreader.home.RFIDBaseActivity;
import com.zebra.demo.rfidreader.rfid.RFIDController;
import com.zebra.demo.rfidreader.settings.adminlogin.ErrorLoginDialogFragment;
import com.zebra.rfid.api3.IEvents;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.IRFIDLogger;
import com.zebra.rfid.api3.LogStates;
import com.zebra.rfid.api3.RFIDResults;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RealTimeLogsType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class LoggerFragment extends Fragment {

    private static final int REQUEST_ID_READ_PERMISSION     = 100;
    private static final int REQUEST_ID_WRITE_PERMISSION    = 200;
    private static final String TAG                         = "RFIDMANAGERAPP-" + LoggerFragment.class.getSimpleName();
    private static RealTimeLogsType selectedRealTimeLogType = RealTimeLogsType.None;
    private final String BUFFEREDLOG_PREFIX                 = "Notification:Dump";
    Context mContext;
    Switch enableDebugLoggerSwitch;
    boolean realtimeLoggerEnabled, debugLoggerEnabled;
    private LinearLayout logsRetrievalLayout;
    private ArrayAdapter<String> realTimeLogsMenuAdapter;
    private Spinner realTimeLogsSpinner;
    private Button retrieveLogsBtn,exportLogsBtn;
    private TextView exportLogsFilePath;
    private boolean canWrite;
    private boolean loggerstatus;
    private IRFIDLogger rfidLogger;
    private String selectedRealTimeLogItem;
    private boolean ignoreDefaultRealTimeSel;



    public static LoggerFragment newInstance() {
        LoggerFragment fragment = new LoggerFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        if( RFIDController.mConnectedReader!= null)
            rfidLogger = RFIDController.mConnectedReader.LOGGER;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootview = inflater.inflate(R.layout.logger_fragment, container, false);
        mContext = rootview.getContext();
        if(RFIDController.mConnectedReader == null )
        {
            Toast.makeText(getActivity(), "Reader not connected", Toast.LENGTH_SHORT).show();

        }
        // visible LinearLayout if and only if enable debug logs is enabled
        logsRetrievalLayout = (LinearLayout)rootview.findViewById(R.id.logs_retrieval_layout);
        // setting switch button for real time logs
        enableDebugLoggerSwitch = (Switch) rootview.findViewById(R.id.enablelogger_switch);
        // Checkbox for retrieving internal ram and flash logs
        retrieveLogsBtn = (Button)rootview.findViewById(R.id.retrievelogbutton);
        // Checkbox for retrieving internal flash logs
        exportLogsBtn = (Button)rootview.findViewById(R.id.export_button);
        realTimeLogsSpinner = (Spinner)rootview.findViewById(R.id.spinner_real_time_logs);
        exportLogsFilePath = (TextView)rootview.findViewById(R.id.export_logs_path_tv);

        // get real time logger states from RFID service
        if(rfidLogger != null ) {
            getCurrentLogStateFromSDKCache();
            bindToRealTimeLogsAdapter(rootview);
            retrieveDefaultEnableDebugLogsSwitchState();
            retrieveDefaultRealTimeLogsSelectionStatus();
            onClickListeners();
        }

        if(RFIDController.mConnectedReader == null || RFIDController.mConnectedReader.getHostName().startsWith("MC33")){
            enableDebugLoggerSwitch.setEnabled(false);
            realTimeLogsSpinner.setEnabled(false);
            retrieveLogsBtn.setEnabled(false);
            exportLogsBtn.setEnabled(false);
        }


        return rootview;
    }

    private void onClickListeners() {
        realTimeLogsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(!ignoreDefaultRealTimeSel) {
                     selectedRealTimeLogItem = parent.getItemAtPosition(position).toString();
                     if(!RFIDBaseActivity.getInstance().getDebugLogsInfo().isEmpty())
                            RFIDBaseActivity.getInstance().clearDebugLogsInfo();
                    processSelLogTypeRealTimeLogs(selectedRealTimeLogItem);
                    if(!retrieveLogsBtn.isEnabled())
                        retrieveLogsBtn.setEnabled(true);
                }
                ignoreDefaultRealTimeSel = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d("realTimeLogsSpinnerSelection", "Nothing selected");
                String selectedItem = parent.getItemAtPosition(0).toString();
                Log.d("realTimeLogsSpinnerSelection:Nothing selected", selectedItem);

            }
        });

        enableDebugLoggerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Enable logsRetrieveLayout only for RFD40/90 models
                if (isRFD40or90Model()) {
                    logsRetrievalLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }

                saveLoggerStateToSdkCache(LogStates.DEBUGLOGGERSTATE, isChecked);
                rfidLogger.EnableDebugLogs(isChecked);
            }
        });

        retrieveLogsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                    try {
                        retrieveLogsBtn.setEnabled(false);
                        Toast.makeText(getActivity(), "Retrieving internal RAM and FLASH logs!", Toast.LENGTH_SHORT).show();
                        rfidLogger.getRfidReaderLogs(LogStates.INTERNALRAMANDFLASHLOGSTATE, true);
                        retrieveLogsBtn.setEnabled(true);
                    } catch (InvalidUsageException e) {
                        retrieveLogsBtn.setEnabled(false);
                        Toast.makeText(getActivity(), "Failed with an Exception", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "setRfidReaderLog failed: Returned SDK Exception"+e.getMessage());
                    } catch (OperationFailureException e) {
                        retrieveLogsBtn.setEnabled(false);
                        if (e.getStatusDescription().equalsIgnoreCase(RFIDResults.ADMIN_CONNECT_ERROR.toString())) {
                            ErrorLoginDialogFragment.handleAdminAuthenticationError(getParentFragmentManager());
                        }
                        Log.d(TAG, "setRfidReaderLog failed: Returned SDK Exception"+e.getMessage());
                    }
                }

        });

        exportLogsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<IEvents.DebugInfoData> debugInfoDataList =  RFIDBaseActivity.getInstance().getDebugLogsInfo();
                if (debugInfoDataList != null && !debugInfoDataList.isEmpty()) {
                    exportLogsBtn.setEnabled(false);
                    Toast.makeText(getActivity(), "Exporting " + selectedRealTimeLogItem + " logs!", Toast.LENGTH_SHORT).show();
                    try {
                        File debugLogsFile = getExportDebugLogsFile(requireActivity(), debugInfoDataList);
                        String path = debugLogsFile.getAbsolutePath() ;
                        Toast.makeText(getActivity(), selectedRealTimeLogItem + " Logs saved at " +path , Toast.LENGTH_SHORT).show();
                        exportLogsFilePath.setSingleLine(false);
                        exportLogsFilePath.setHorizontallyScrolling(false);
                        exportLogsFilePath.setMaxLines(3);
                        exportLogsFilePath.setText(path);
                        exportLogsBtn.setEnabled(true);
                    } catch (IOException e) {
                        Toast.makeText(getActivity(), "Failed with an Exception", Toast.LENGTH_SHORT).show();
                        exportLogsBtn.setEnabled(false);
                        Toast.makeText(getActivity(), "Failed to export " + selectedRealTimeLogItem + " Logs !", Toast.LENGTH_SHORT).show();
                    }
                    RFIDBaseActivity.getInstance().clearDebugLogsInfo();
                }else
                    Toast.makeText(getActivity(), " Please retrieve logs first !!", Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void bindToRealTimeLogsAdapter(View rootview) {
        realTimeLogsMenuAdapter = new ArrayAdapter<>(mContext,
                android.R.layout.simple_spinner_item, rootview.getResources().getStringArray(R.array.real_time_logs_menu_array));
        realTimeLogsMenuAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        realTimeLogsSpinner.setAdapter(realTimeLogsMenuAdapter);
    }

    private void getCurrentLogStateFromSDKCache() {
        realtimeLoggerEnabled = rfidLogger.getLogConfig(LogStates.REALTIMELOGGERSTATE);
        debugLoggerEnabled = rfidLogger.getLogConfig(LogStates.DEBUGLOGGERSTATE);

        // debug out put
        Log.d(TAG, "RealtimeLoggerState: " + realtimeLoggerEnabled + ", DebugLoggerState: "
                + debugLoggerEnabled);
    }

    /* Retrieve the default "Enable Debug logs" Switch log state */
    private void retrieveDefaultEnableDebugLogsSwitchState() {
         enableDebugLoggerSwitch.setChecked(debugLoggerEnabled);
        // Enable logsRetrieveLayout only for RFD40/90 models
        if (isRFD40or90Model()&& debugLoggerEnabled) {
            logsRetrievalLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean isRFD40or90Model(){
        return (RFIDController.mConnectedReader.getHostName().startsWith("RFD40") ||
                RFIDController.mConnectedReader.getHostName().startsWith("RFD90"));
    }

    /* Retrieve the default selection of "RealTimeLogs" state */
    private void retrieveDefaultRealTimeLogsSelectionStatus() {
        RealTimeLogsType initialLogType = realtimeLoggerEnabled ?
                selectedRealTimeLogType : RealTimeLogsType.None;
        ignoreDefaultRealTimeSel = true;
        realTimeLogsSpinner.setSelection(getRealTimeLogsSpinnerPosition(initialLogType));
        selectedRealTimeLogItem = initialLogType.toString();
    }

    private int getRealTimeLogsSpinnerPosition(RealTimeLogsType realTimeLogsType) {
        switch (realTimeLogsType) {
            case Error:
                return 1;
            case Warning:
                return 2;
            case Verbose:
                return 3;
            case None:
                return 0;
            default:
                if(!RFIDBaseActivity.getInstance().getDebugLogsInfo().isEmpty())
                    RFIDBaseActivity.getInstance().clearDebugLogsInfo();
                return 0;
        }
    }

    private void processSelLogTypeRealTimeLogs(String selectedItem) {
        applyRealTimeLogsSelection(RealTimeLogsType.valueOf(selectedItem));
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void DisableSettingsUI() {
        enableDebugLoggerSwitch.setEnabled(false);
    }

    private void EnableSettingsUI() {
        enableDebugLoggerSwitch.setEnabled(true);
    }

    /**
     * Saves a logger state into the SDK cache.
     *
     * This updates the in-memory value that {@code getLogConfig(...)} returns later,
     * without sending anything to the reader hardware.
     */
    private void saveLoggerStateToSdkCache(LogStates logState, boolean enabled) {
        rfidLogger.setLogConfig(logState, enabled);
    }

    /**
     * Applies the selected real-time log type to both local SDK state and the connected reader.
     *
     * Two SDK calls are required because they serve different layers:
     *
     *   1. setLogConfig  → updates the SDK's in-memory logmap (local cache).
     *                      This is what getLogConfig() reads back later
     *                      (e.g. in ConnectionController.updateReaderConnection).
     *
     *   2. setRfidRealTimeReaderLog → sends the actual command to the reader hardware.
     *                         This can throw InvalidUsageException / OperationFailureException.
     *
     * Ideally the SDK would call setLogConfig internally inside setRfidReaderLog
     * so callers only need one call, but since we can't modify the SDK, this
     * wrapper method acts as the single entry-point that enforces both steps.
     */
    private void applyRealTimeLogsSelection(RealTimeLogsType realTimeLogsType) {
        selectedRealTimeLogType = realTimeLogsType;
        selectedRealTimeLogItem = realTimeLogsType.toString();
        boolean isRealTimeLogEnabled = realTimeLogsType != RealTimeLogsType.None;

        // Step 1: update local SDK cache so getLogConfig() reflects the new state
        saveLoggerStateToSdkCache(LogStates.REALTIMELOGGERSTATE, isRealTimeLogEnabled);

        // Step 2: push the change to the reader hardware
        try {
            rfidLogger.setRfidRealTimeReaderLog(realTimeLogsType, isRealTimeLogEnabled);
        } catch (InvalidUsageException e) {
            Log.d(TAG, "setRfidReaderLog failed: Returned SDK Exception"+e.getMessage());
        } catch (OperationFailureException e) {
            if (e.getStatusDescription().equalsIgnoreCase(RFIDResults.ADMIN_CONNECT_ERROR.toString())) {
                ErrorLoginDialogFragment.handleAdminAuthenticationError(getParentFragmentManager());
            }
            Log.d(TAG, "setRfidReaderLog failed: Returned SDK Exception"+e.getMessage());
        }
    }

    private void askPermissionAndWriteFile() {
        canWrite = this.askPermission(REQUEST_ID_WRITE_PERMISSION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
       /* if (canWrite) {
            this.writeFile();

        }*/
    }

    // With Android Level >= 23, you have to ask the user
    // for permission with device (For example read/write data on the device).
    private boolean askPermission(int requestId, String permissionName) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            // Check if we have permission
            int permission = ActivityCompat.checkSelfPermission(getActivity(), permissionName);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // If don't have permission so prompt the user.
                this.requestPermissions(
                        new String[]{permissionName},
                        requestId
                );
                return false;
            }
        }
        return true;
    }

    // When you have the request results
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //
        // Note: If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0) {
            switch (requestCode) {
                case REQUEST_ID_READ_PERMISSION: {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    }
                }
                case REQUEST_ID_WRITE_PERMISSION: {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        //new WritefileTask().execute();
                    }
                }
            }
        } else {
            Toast.makeText(getActivity(), "Permission Cancelled!", Toast.LENGTH_SHORT).show();
            Log.d( TAG, "Permission Cancelled!" );
        }
    }



    public void onDataReceived(String data) {
        if (data.contains(Command_Log.commandName.toLowerCase())) {
            handleStatusResponse(data);
        }
        if (data.contains(BUFFEREDLOG_PREFIX)) {
            Log.d(TAG, data);
        }
    }

    public void handleStatusResponse(final String data) {
        Response_Status statusData = Response_Status.FromString(data);
        if (statusData.command.trim().equalsIgnoreCase(Command_Log.commandName)) {
            if (statusData.Status.trim().equalsIgnoreCase("OK")) {
                loggerstatus = true;
            }
        }
    }

    public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
        //Intent intent;
        //intent = new Intent(getActivity(), DeviceDiscoverActivity.class);
        //intent.putExtra("enable_toolbar", false);
       // startActivity(intent);

        //getActivity().finish();

    }
    public static File getExportDebugLogsFile(Context context,List<IEvents.DebugInfoData> debugInfoDataList) throws IOException
    {
        //Get app-specific external directory
        File debugLogsDir  = context.getExternalFilesDir(null);
        if(debugLogsDir == null) {
            throw new IOException("External storage not available");
        }
        String fileName = "123_rfid_debug_logs_"+System.currentTimeMillis()+".txt";
        File debugLogsFile = new File(debugLogsDir,fileName);
        try(FileWriter writer = new FileWriter(debugLogsFile)){
            for (IEvents.DebugInfoData debugInfoData:debugInfoDataList)
            {
                String debugInfoLevel = debugInfoData.getLevel();
                Log.d("LoggerFragment:","exportLogsBtn:Level: "+debugInfoLevel);
                String debugInfoMsg = debugInfoData.getMessage();
                Log.d("LoggerFragment:","exportLogsBtn:Message: "+debugInfoMsg);
                writer.write(debugInfoData.getMessage()+"\n");
            }
        }
        return debugLogsFile;

    }
}

