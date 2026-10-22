package com.zebra.demo.rfidreader.reader_connection;

import static com.zebra.rfid.api3.GenericUtil.isQCDevice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zebra.demo.ActiveDeviceActivity;
import com.zebra.demo.DeviceDiscoverActivity;
import com.zebra.demo.R;
import com.zebra.demo.rfidreader.common.Constants;
import com.zebra.demo.rfidreader.rfid.RFIDController;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;

import java.util.ArrayList;

public class PairedReaderListAdapter extends RecyclerView.Adapter<PairedReaderListAdapter.ViewHolder> {
    private final Context context;
    private final int resourceId;
    private final ArrayList<ReaderDevice> readersList;
    final private ListItemClickListener mOnClickListener;

    View view;

    public PairedReaderListAdapter(Context context, int resourceId, ArrayList<ReaderDevice> readersList, ListItemClickListener mOnClickListener) {
        this.context = context;
        this.resourceId = resourceId;
        this.readersList = readersList;
        this.mOnClickListener = mOnClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        view = LayoutInflater.from(context).inflate(R.layout.paired_reader_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReaderDevice reader = readersList.get(position);
        RFIDReader rfidReader = reader.getRFIDReader();
        holder.deviceName.setText(reader.getName());
        String modelName = reader.getName();
        //modelName = reader.getRFIDReader().ReaderCapabilities.getModelName();

        String model = reader.getDeviceCapability(modelName);
        if(model.equals("STANDARD") || model.equals("RFD8500") || model.startsWith("MC33")) {
            holder.icon.setImageResource(R.drawable.ic_standard);
        } else if(model.equals("PREMIUM (WiFi)")) {
            holder.icon.setImageResource(R.drawable.ic_premium);
        } else {
            holder.icon.setImageResource(R.drawable.ic_premium_plus);
        }
        holder.readermodel.setText(String.format(context.getResources().getString(R.string.readermodel).toString(),model));

        if(model.equals("QC")){
            String ser_no = reader.getSerialNumber();
            if(ser_no!=null)holder.serialNo.setText("SERIAL: "+ ser_no);else holder.serialNo.setText("SERIAL: " );
        }
        if(model.equals("RFD8500")){
            String serialno[] = modelName.split(model);
            holder.serialNo.setText("SERIAL: "+serialno[1]);
        }
        if(model.startsWith("RE40")){
            String ser_no = reader.getSerialNumber();
            if(ser_no!=null)holder.serialNo.setText("SERIAL: "+ ser_no);else holder.serialNo.setText("SERIAL: " );
        }
        if(model.equals("FXP20")){
            String ser_no = reader.getSerialNumber();
            if(ser_no!=null)holder.serialNo.setText("SERIAL: "+ ser_no);else holder.serialNo.setText("SERIAL: " );
        }
        if(model.startsWith("MC33")){
            String serialno[] = modelName.split("R");
            holder.serialNo.setText("SERIAL: "+serialno[1]);
        }
        if(modelName.startsWith("RFD40")){
            String[] splitStr = modelName.split("-");

            if(splitStr[0].startsWith("RFD40+") || splitStr[0].startsWith("RFD40P")){
                String serialno[] = modelName.split("_");
                int length = serialno.length;
                holder.serialNo.setText("SERIAL: "+serialno[length-1]);
            }
            if(splitStr[0].equals("RFD4030") || splitStr[0].equals("RFD4031") || splitStr[0].equals("RFD4051")){
                String serialNumber = reader.getSerialNumber();
                if (serialNumber != null && serialNumber.contains("S/N:")) {
                    String[] serNo = serialNumber.split("S/N:", 2);
                    holder.serialNo.setText("SERIAL: " + (serNo.length > 1 ? serNo[1] : ""));
                } else {
                    holder.serialNo.setText("SERIAL: " + (serialNumber != null ? serialNumber : ""));
                }
            }
        }
        if(modelName.startsWith("RFD90")) {
            String[] splitStr = modelName.split("-");
            if(splitStr[0].startsWith("RFD90+") || splitStr[0].startsWith("RFD90P")){
                String serialno[] = modelName.split("_");
                int length = serialno.length;
                holder.serialNo.setText("SERIAL: "+serialno[length-1]);
            }else if(splitStr[0].startsWith("RFD90")){
                String serialNumber = reader.getSerialNumber();
                if (serialNumber != null && serialNumber.contains("S/N:")) {
                    String[] serNo = serialNumber.split("S/N:", 2);
                    holder.serialNo.setText("SERIAL: " + (serNo.length > 1 ? serNo[1] : ""));
                } else {
                    holder.serialNo.setText("SERIAL: " + (serialNumber != null ? serialNumber : ""));
                }
            }
        }
        if(modelName.startsWith("TC27R")||(modelName.startsWith("TC22R"))){
            String ser_no = reader.getSerialNumber();
            if(ser_no!=null)holder.serialNo.setText("SERIAL: "+ ser_no);else holder.serialNo.setText("SERIAL: " );
        }



        /*if (rfidReader.getTransport() == "BLUETOOTH") {
            holder.icon.setImageResource(R.drawable.ic_action_bluetooth_connected);
        } else if (rfidReader.getTransport() == "SERVICE_SERIAL") {
            holder.icon.setImageResource(R.drawable.ic_serial_connection);
        } else {
            holder.icon.setImageResource(R.drawable.ic_action_usb);
        }
        if (rfidReader.ReaderCapabilities != null && rfidReader.ReaderCapabilities.getBDAddress() != null) {
            if (rfidReader.getTransport() == "BLUETOOTH") {
                holder.icon.setImageResource(R.drawable.ic_action_bluetooth_connected);
            } else if (rfidReader.getTransport() == "SERVICE_SERIAL") {
                holder.icon.setImageResource(R.drawable.ic_serial_connection);
            } else {
                holder.icon.setImageResource(R.drawable.ic_action_usb);
            }
        }*/

        holder.serialNo.setVisibility(Constants.showSerialNo? View.VISIBLE:View.GONE);

        holder.options_menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu menu = new PopupMenu(context, holder.options_menu);
                menu.inflate(R.menu.options_menu);

                if(rfidReader.getHostName().startsWith("RFID-")) {
                    String wwanDevice = getSystemProperty("ro.boot.device.wwan_device", "false");
                    MenuItem connectItem = menu.getMenu().findItem(R.id.connect);
                    if (wwanDevice.equalsIgnoreCase("true")) {
                        connectItem.setTitle("Connect with RFID+WWAN");
                    } else {
                        connectItem.setTitle("Connect");
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    menu.setForceShowIcon(true);
                }
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.connect:
                                RFIDController.CONNECT_RFID_WWAN = true;
                                mOnClickListener.ConnectReader(position);
                                break;
                            case R.id.unpair:
                                mOnClickListener.unPair(position, rfidReader.getTransport());
                                break;
                            case R.id.reader_details:

                                if(context instanceof ActiveDeviceActivity) {
                                    ((ActiveDeviceActivity) context).loadReaderDetails(reader);
                                } else if(context instanceof DeviceDiscoverActivity) {
                                    ((DeviceDiscoverActivity) context).loadReaderDetails(reader);
                                }
                                break;
                        }
                        return false;
                    }
                });
                menu.show();
            }
        });

    }

    public static String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class, String.class)
                    .invoke(null, key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public int getItemCount() {
       return readersList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView deviceName;
        private ImageView options_menu;
        private TextView readermodel;
        private TextView serialNo;
        Context ctx;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.pairedreader_icon);
            deviceName = itemView.findViewById(R.id.pairedreader_serialno);
            options_menu = itemView.findViewById(R.id.options_menu);
            readermodel = itemView.findViewById(R.id.model);
            serialNo = itemView.findViewById(R.id.serialno);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RFIDController.CONNECT_RFID_WWAN = false;
                    mOnClickListener.ConnectReader(getAdapterPosition());
                }
            });

            //options_menu.setOnClickListener(this);
            // itemView.setOnClickListener(this);
        }

    }

    interface ListItemClickListener {
        void onListItemClick(View view);
        public void ConnectReader(int position);
        void unPair(int position,String transportType);

    }


    /**
     * method to get connect password for the reader
     *
     * @param address - device BT address
     * @return connect password of the reader
     */
    private String getReaderPassword(String address) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.READER_PASSWORDS, 0);
        return sharedPreferences.getString(address, null);
    }
}
