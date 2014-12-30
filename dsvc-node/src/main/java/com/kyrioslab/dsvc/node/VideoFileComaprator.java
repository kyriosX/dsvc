package com.kyrioslab.dsvc.node;

import java.io.File;
import java.util.Comparator;

/**
 * Created by Ivan Kirilyuk on 30.12.14.
 */
public class VideoFileComaprator implements Comparator<File>{

    @Override
    public int compare(File o1, File o2) {
        int num1 = Integer.valueOf(o1.getName().substring(0, o1.getName().indexOf(".")));
        int num2 = Integer.valueOf(o2.getName().substring(0, o2.getName().indexOf(".")));
        if (num1 > num2) {
            return 1;
        } else if ( num1 < num2 ) {
            return -1;
        } else {
            return 0;
        }
    }
}
