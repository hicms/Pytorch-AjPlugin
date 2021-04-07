package com.hraps.pytorch.od;

import android.graphics.Rect;

import com.hraps.pytorch.od.OdResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class PrePostProcessor {
    // for yolov5 model, no need to apply MEAN and STD
    public static float[] NO_MEAN_RGB = new float[] {0.0f, 0.0f, 0.0f};
    public static float[] NO_STD_RGB = new float[] {1.0f, 1.0f, 1.0f};

    // model input image size
    public static int mInputWidth = 640;
    public static int mInputHeight = 640;

    // model output is of size 25200*85
    private static int mOutputRow = 25200; // as decided by the YOLOv5 model for input image of size 640*640
    private static int mOutputColumn = 85; // left, top, right, bottom, score and 80 class probability
    private static float mThreshold = 0.30f; // score above which a detection is generated
    private static int mNmsLimit = 20;

    public static String[] mClasses;

    // The two methods nonMaxSuppression and IOU below are ported from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift
    /**
     Removes bounding boxes that overlap too much with other boxes that have
     a higher score.
     - Parameters:
     - boxes: an array of bounding boxes and their scores
     - limit: the maximum number of boxes that will be selected
     - threshold: used to decide whether boxes overlap too much
     */
    public static ArrayList<OdResult> nonMaxSuppression(ArrayList<OdResult> boxes, int limit, float threshold) {

        // Do an argsort on the confidence scores, from high to low.
        Collections.sort(boxes,
                new Comparator<OdResult>() {
                    @Override
                    public int compare(OdResult o1, OdResult o2) {
                        return o1.score.compareTo(o2.score);
                    }
                });

        ArrayList<OdResult> selected = new ArrayList<>();
        boolean[] active = new boolean[boxes.size()];
        Arrays.fill(active, true);
        int numActive = active.length;

        // The algorithm is simple: Start with the box that has the highest score.
        // Remove any remaining boxes that overlap it more than the given threshold
        // amount. If there are any boxes left (i.e. these did not overlap with any
        // previous boxes), then repeat this procedure, until no more boxes remain
        // or the limit has been reached.
        boolean done = false;
        for (int i=0; i<boxes.size() && !done; i++) {
            if (active[i]) {
                OdResult boxA = boxes.get(i);
                selected.add(boxA);
                if (selected.size() >= limit) break;

                for (int j=i+1; j<boxes.size(); j++) {
                    if (active[j]) {
                        OdResult boxB = boxes.get(j);
                        if (IOU(boxA.rect, boxB.rect) > threshold) {
                            active[j] = false;
                            numActive -= 1;
                            if (numActive <= 0) {
                                done = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return selected;
    }

    /**
     Computes intersection-over-union overlap between two bounding boxes.
     */
    public static float IOU(Rect a, Rect b) {
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        if (areaA <= 0.0) return 0.0f;

        float areaB = (b.right - b.left) * (b.bottom - b.top);
        if (areaB <= 0.0) return 0.0f;

        float intersectionMinX = Math.max(a.left, b.left);
        float intersectionMinY = Math.max(a.top, b.top);
        float intersectionMaxX = Math.min(a.right, b.right);
        float intersectionMaxY = Math.min(a.bottom, b.bottom);
        float intersectionArea = Math.max(intersectionMaxY - intersectionMinY, 0) *
                Math.max(intersectionMaxX - intersectionMinX, 0);
        return intersectionArea / (areaA + areaB - intersectionArea);
    }

    public static ArrayList<OdResult> outputsToNMSPredictions(float[] outputs, float imgScaleX, float imgScaleY, float ivScaleX, float ivScaleY, float startX, float startY) {
        ArrayList<OdResult> odResults = new ArrayList<>();
        for (int i = 0; i< mOutputRow; i++) {
            if (outputs[i* mOutputColumn +4] > mThreshold) {
                float x = outputs[i* mOutputColumn];
                float y = outputs[i* mOutputColumn +1];
                float w = outputs[i* mOutputColumn +2];
                float h = outputs[i* mOutputColumn +3];

                float left = imgScaleX * (x - w/2);
                float top = imgScaleY * (y - h/2);
                float right = imgScaleX * (x + w/2);
                float bottom = imgScaleY * (y + h/2);

                float max = outputs[i* mOutputColumn +5];
                int cls = 0;
                for (int j = 0; j < mOutputColumn -5; j++) {
                    if (outputs[i* mOutputColumn +5+j] > max) {
                        max = outputs[i* mOutputColumn +5+j];
                        cls = j;
                    }
                }

                Rect rect = new Rect((int)(startX+ivScaleX*left), (int)(startY+top*ivScaleY), (int)(startX+ivScaleX*right), (int)(startY+ivScaleY*bottom));
                OdResult odResult = new OdResult(cls, outputs[i*mOutputColumn+4], rect);
                odResults.add(odResult);
            }
        }
        return nonMaxSuppression(odResults, mNmsLimit, mThreshold);
    }


}