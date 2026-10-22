package com.zebra.demo.rfidreader.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zebra.demo.ActiveDeviceActivity;
import com.zebra.demo.R;
import com.zebra.demo.rfidreader.rfid.RFIDController;


/**
 * Fragment that displays a message indicating Battery Statistics are not supported for FXP20 devices.
 */
public class BatteryFragementFXP20 extends BackPressedFragment{

    public BatteryFragementFXP20() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of this fragment.
     *
     * @return A new instance of BatteryFragmentFXP20
     */
    public static BatteryFragementFXP20 newInstance() {
        return new BatteryFragementFXP20();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        if (RFIDController.mConnectedReader == null) {

            Toast.makeText(getContext(), "No device in connected state", Toast.LENGTH_SHORT).show();

            return new View(getContext());
        }

        return inflater.inflate(R.layout.fragment_profiles_not_supported, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView batteryStatisticsTextView = view.findViewById(R.id.text_profiles_not_supported);
        if (batteryStatisticsTextView != null) {
            batteryStatisticsTextView.setText(requireContext().getResources().getString(R.string.BatteryStatistics_FXP20));
        }
    }
    @Override
    public void onBackPressed() {
        if (getActivity() instanceof SettingsDetailActivity)
            ((SettingsDetailActivity) getActivity()).callBackPressed();
        else
            ((ActiveDeviceActivity) getActivity()).callBackPressed();

    }
}
