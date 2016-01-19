package org.xdty.phone.number.model.offline;

import android.content.Context;
import android.text.TextUtils;

import org.xdty.phone.number.R;
import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.Type;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class OfflineRecord implements INumber<OfflineRecord> {
    private final static int PHONE_FMT_LENGTH = 9;
    private Context context;
    private Record mRecord;

    public OfflineRecord(Context context) {
        this.context = context;
    }

    public static int byteArrayToLeInt(byte[] b) {
        final ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt();
    }

    public static byte[] leIntToByteArray(int i) {
        final ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(i);
        return bb.array();
    }

    String humanReadableType(int type) {
        switch (type) {
            case 1:
                return "移动";
            case 2:
                return "联通";
            case 3:
                return "电信";
            case 4:
                return "电信虚拟运营商";
            case 5:
                return "联通虚拟运营商";
            case 6:
                return "移动虚拟运营商";
            default:
                return "未知运营商";
        }
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getProvince() {
        return mRecord.province;
    }

    @Override
    public Type getType() {
        return Type.NORMAL;
    }

    @Override
    public String getCity() {
        return mRecord.city;
    }

    @Override
    public String getNumber() {
        return mRecord.number;
    }

    @Override
    public String getProvider() {
        return mRecord.operators;
    }

    @Override
    public String url() {
        return null;
    }

    @Override
    public String key() {
        return null;
    }

    @Override
    public OfflineRecord find(String number) {
        number = number.replaceAll("\\+", "");
        if (number.length() < 7 || number.length() > 11) {
            return null;
        }

        try {
            mRecord = null;

            int phone = Integer.parseInt(number.substring(0, 7));
            File file = createCacheFile(context, "phone.dat", R.raw.phone);
            long length = file.length();
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            byte bVersion[] = new byte[4];
            raf.read(bVersion);
            byte bFirstOffset[] = new byte[4];
            raf.read(bFirstOffset);
            int firstOffset = byteArrayToLeInt(bFirstOffset);
            raf.seek(firstOffset);
            int left = 0;
            int right = (int) (length - firstOffset) / PHONE_FMT_LENGTH;

            int middle;
            int currentOffset;
            int currentPhone;

            byte bNumber[] = new byte[4];

            while (left <= right) {
                middle = (right + left) / 2;
                currentOffset = firstOffset + middle * PHONE_FMT_LENGTH;
                if (currentOffset >= length) {
                    break;
                }
                raf.seek(currentOffset);
                raf.read(bNumber);
                currentPhone = byteArrayToLeInt(bNumber);
                if (currentPhone > phone) {
                    right = middle - 1;
                } else if (currentPhone < phone) {
                    left = middle + 1;
                } else {
                    raf.seek(currentOffset);
                    byte bRecord[] = new byte[PHONE_FMT_LENGTH];
                    raf.read(bRecord);
                    Index index = new Index(bRecord);
                    raf.seek(index.offset);

                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    while (true) {
                        int nextByte = raf.read();
                        if (nextByte == '\0') {
                            break;
                        } else {
                            b.write(nextByte);
                        }
                    }
                    mRecord = new Record(new String(b.toByteArray()), number, index.type);
                    b.close();
                    break;
                }
            }

            if (mRecord != null) {
                return this;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public boolean isValid() {
        return !TextUtils.isEmpty(getNumber());
    }

    @Override
    public int getApiId() {
        return INumber.API_ID_OFFLINE;
    }

    private File createCacheFile(Context context, String filename, int raw) throws IOException {
        File cacheFile = new File(context.getCacheDir(), filename);

        if (cacheFile.exists()) {
            return cacheFile;
        }

        InputStream inputStream = context.getResources().openRawResource(raw);
        FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            fileOutputStream.write(buffer, 0, length);
        }

        try {
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return cacheFile;
    }

    public class Record {
        String province;
        String city;
        String zip;
        String area;
        String operators;
        String number;

        public Record(String s, String n, int type) {
            String[] a = s.split("\\|");
            province = a[0];
            city = a[1];
            zip = a[2];
            area = a[3];
            number = n;
            operators = humanReadableType(type);
        }

        public String toString() {
            return province + ", " + city + ", " + zip + ", " + area;
        }
    }

    public class Index {
        int number;
        int offset;
        int type;

        public Index(byte[] data) {
            number = byteArrayToLeInt(Arrays.copyOfRange(data, 0, 4));
            offset = byteArrayToLeInt(Arrays.copyOfRange(data, 4, 8));
            type = data[8];
        }

        public String toString() {
            return number + ", " + offset + ", " + type;
        }
    }
}