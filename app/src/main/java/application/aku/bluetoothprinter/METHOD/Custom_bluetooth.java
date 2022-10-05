package application.aku.bluetoothprinter.METHOD;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import application.aku.bluetoothprinter.R;

public class Custom_bluetooth extends ArrayAdapter<String> {
    private final Activity context;
    private ArrayList<Object_bluetooth> listItem;

    public Custom_bluetooth(Activity context, ArrayList listItem) {
        super(context, R.layout.list_bluetooth, listItem);
        this.context = context;
        this.listItem = listItem;
    }

    @Override
    public View getView(final int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.list_bluetooth, null, true);

        TextView tvname = rowView.findViewById(R.id.tvname);
        TextView tvaddress = rowView.findViewById(R.id.tvaddress);

        try {
            tvname.setText(listItem.get(position).getName());
            tvaddress.setText(listItem.get(position).getAddress());
        }catch (Exception e){
            e.printStackTrace();
        }

        return rowView;
    }
}
