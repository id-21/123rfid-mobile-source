package com.zebra.demo.rfidreader.settings;

import static com.zebra.demo.scanner.helpers.ActiveDeviceAdapter.INVENTORY_PROTOCOL;
import static com.zebra.demo.scanner.helpers.ActiveDeviceAdapter.MAIN_HOME_SETTINGS_TAB;
import static com.zebra.demo.scanner.helpers.ActiveDeviceAdapter.RFID_ADVANCED_OPTIONS_TAB;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.demo.ActiveDeviceActivity;
import com.zebra.demo.R;
import com.zebra.demo.rfidreader.rfid.RFIDController;
import com.zebra.rfid.api3.ENUM_INVENTORY_PROTOCOL;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link InventoryProtocolFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class InventoryProtocolFragment extends BackPressedFragment {


    private RadioGroup inventoryProtocolRadioGroup;
    private RadioButton rbProtocolGen2;
    private RadioButton rbProtocolGen2X;
    private RadioButton rbProtocolHybrid;
    private String TAG = "InventoryProtocolFragment";

    private static final Map<Integer, ENUM_INVENTORY_PROTOCOL> PROTOCOL_MAP = new HashMap<>();
    static {
        PROTOCOL_MAP.put(R.id.rbProtocolGen2, ENUM_INVENTORY_PROTOCOL.GEN2);
        PROTOCOL_MAP.put(R.id.rbProtocolGen2X, ENUM_INVENTORY_PROTOCOL.GEN2X);
        PROTOCOL_MAP.put(R.id.rbProtocolHybrid, ENUM_INVENTORY_PROTOCOL.HYBRID);
    }

    private static Integer getRadioIdForProtocol(ENUM_INVENTORY_PROTOCOL protocol) {
        for (Map.Entry<Integer, ENUM_INVENTORY_PROTOCOL> entry : PROTOCOL_MAP.entrySet()) {
            if (entry.getValue() == protocol) return entry.getKey();
        }
        return null;
    }

    public InventoryProtocolFragment() {

    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment InventoryProtocolFragment1.
     */

    public static InventoryProtocolFragment newInstance() {
        return new InventoryProtocolFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_inventory_protocol, container, false);


        TextView inventoryProtocolLabel = root.findViewById(R.id.inventoryProtocolLabel);
        inventoryProtocolRadioGroup = root.findViewById(R.id.inventoryProtocolRadioGroup);
        rbProtocolGen2 = root.findViewById(R.id.rbProtocolGen2);
        rbProtocolGen2X = root.findViewById(R.id.rbProtocolGen2X);
        rbProtocolHybrid = root.findViewById(R.id.rbProtocolHybrid);
        TextView tvNotSupported = root.findViewById(R.id.tvNotSupported);

        if (RFIDController.mConnectedReader != null && RFIDController.mConnectedReader.isConnected()){
            if( isInventoryProtocolSupported()) {
                inventoryProtocolLabel.setVisibility(View.VISIBLE);
                inventoryProtocolRadioGroup.setVisibility(View.VISIBLE);
                tvNotSupported.setVisibility(View.GONE);

                ENUM_INVENTORY_PROTOCOL currentInventoryProtocol = getInventoryProtocol();

                if (currentInventoryProtocol != null) {
                    Integer radioId = getRadioIdForProtocol(currentInventoryProtocol);
                    if (radioId != null) {
                        inventoryProtocolRadioGroup.check(radioId);
                    }
                }
            } else {
                inventoryProtocolLabel.setVisibility(View.GONE);
                inventoryProtocolRadioGroup.setVisibility(View.GONE);
                tvNotSupported.setVisibility(View.VISIBLE);
            }
        }

        return root;
    }

    public ENUM_INVENTORY_PROTOCOL getInventoryProtocol() {
        ENUM_INVENTORY_PROTOCOL inventoryProtocol = null;
        if((RFIDController.mConnectedReader == null || !RFIDController.mConnectedReader.isConnected())){
            Toast.makeText(getActivity(), "No reader connected", Toast.LENGTH_SHORT).show();
            return inventoryProtocol;
        }

        boolean isSupported = RFIDController.mConnectedReader.getHostName().startsWith("RFD40")
                    || RFIDController.mConnectedReader.getHostName().startsWith("RFD90");

        if(isSupported){
            try {
                inventoryProtocol = RFIDController.mConnectedReader.Config.getInventoryProtocol();
            } catch (InvalidUsageException e) {
                Log.d(TAG,  "Returned SDK Exception");
            } catch (OperationFailureException e) {
                Log.d(TAG,  "Returned SDK Operation Failure Exception " + e.getVendorMessage());
                // Reader may reject this read while inventory is running; keep last known value.
                inventoryProtocol = RFIDController.inventoryProtocol;
            }

            RFIDController.inventoryProtocol = inventoryProtocol;
        }

        return inventoryProtocol;
    }

    @Override
    public void onBackPressed() {
        if (isInventoryProtocolSupported()) {
            int checkedId = inventoryProtocolRadioGroup.getCheckedRadioButtonId();

            ENUM_INVENTORY_PROTOCOL protocol = PROTOCOL_MAP.get(checkedId);
            if (protocol != null && protocol != RFIDController.inventoryProtocol) {
                saveInventoryProtocol(protocol);
            }
        }

        if(getActivity() instanceof SettingsDetailActivity)
            ((SettingsDetailActivity) getActivity()).callBackPressed();
        if(getActivity() instanceof ActiveDeviceActivity) {
            ((ActiveDeviceActivity) getActivity()).callBackPressed();
            ((ActiveDeviceActivity) getActivity()).loadNextFragment(RFID_ADVANCED_OPTIONS_TAB);
        }
    }

    public void saveInventoryProtocol(ENUM_INVENTORY_PROTOCOL protocol) {
        if (RFIDController.mIsInventoryRunning) {
            Toast.makeText(getActivity(), "Failed to apply settings \n Opeartion in Progress-Command Not Allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            RFIDController.mConnectedReader.Config.setInventoryProtocol(protocol);
            RFIDController.inventoryProtocol = protocol;
            Toast.makeText(getActivity(), "Inventory Protocol set to " + protocol.toString(), Toast.LENGTH_SHORT).show();

        } catch (InvalidUsageException e) {
            Log.d(TAG, "Returned SDK Exception");
        } catch (OperationFailureException e) {
            Log.d(TAG, "Returned SDK Operation Failure Exception");
        }
    }

    public static boolean isInventoryProtocolSupported() {
        boolean isSupported = false;

        try {
            if(RFIDController.mConnectedReader != null && RFIDController.mConnectedReader.isConnected()){
                isSupported = RFIDController.mConnectedReader.getHostName().startsWith("RFD40")
                        || RFIDController.mConnectedReader.getHostName().startsWith("RFD90");

                if(isSupported && !RFIDController.mIsInventoryRunning){
                    RFIDController.mConnectedReader.Config.getInventoryProtocol();
                }
            }
        } catch (InvalidUsageException e) {
            Log.d("InventoryProtocolFragment",  "Returned Invalid Usage SDK Exception");
            isSupported = false;
        } catch (OperationFailureException e) {
            Log.d("InventoryProtocolFragment",  "Returned Operation Failure SDK Exception " + e.getVendorMessage());
            isSupported = false;
        }

        return isSupported;
    }
}