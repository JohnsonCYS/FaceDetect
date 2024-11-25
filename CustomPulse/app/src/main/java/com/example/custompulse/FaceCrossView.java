package com.example.custompulse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class FaceCrossView extends View {

    private Paint paint;
    private float centerX, centerY;
    private int crossWidth, crossHeight;

    public FaceCrossView(Context context) {
        super(context);
        init();
    }

    public FaceCrossView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceCrossView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xFFFFFFFF);  // 设置十字架为白色
        paint.setStrokeWidth(2f);  // 设置线条宽度
        paint.setStyle(Paint.Style.FILL);  // 填充样式
        paint.setAntiAlias(true);  // 启用抗锯齿
    }

    public void updateFacePosition(float centerX, float centerY, int width, int height) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.crossWidth = width;
        this.crossHeight = height;
        invalidate();  // 刷新视图
    }

    public void hideCross() {
        this.centerX = -1;  // 隐藏十字架
        this.centerY = -1;
        invalidate();  // 刷新视图
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (centerX == -1 || centerY == -1) {
            return;  // 如果十字架不显示，则跳过绘制
        }

        // 绘制中心的白色实心圆
        float centerDotRadius = 5f;  // 圆点半径
        canvas.drawCircle(centerX, centerY, centerDotRadius, paint);

        // 计算十字架的四个端点
        int halfWidth = crossWidth / 2;
        int halfHeight = crossHeight / 2;

        // 十字架的横竖线段
        canvas.drawLine(centerX - halfWidth, centerY, centerX + halfWidth, centerY, paint);  // 水平线
        canvas.drawLine(centerX, centerY - halfHeight, centerX, centerY + halfHeight, paint);  // 垂直线

        // 画十字架的四个端点三角形
        drawTriangle(canvas, centerX - halfWidth, centerY, "left");  // 左端点
        drawTriangle(canvas, centerX + halfWidth, centerY, "right");  // 右端点
        drawTriangle(canvas, centerX, centerY - halfHeight, "up");  // 上端点
        drawTriangle(canvas, centerX, centerY + halfHeight, "down");  // 下端点
    }
    private void drawTriangle(Canvas canvas, float x, float y, String direction) {
        float triangleSize = 20f;  // 三角形的大小
        float halfSize = triangleSize / 2;

        Path path = new Path();

        switch (direction) {
            case "left":
                // 计算三角形底边的起始位置
                path.moveTo(x, y);  // 顶点
                path.lineTo(x - triangleSize, y - halfSize);  // 左下
                path.lineTo(x - triangleSize, y + halfSize);  // 左上
                break;
            case "up":
                // 计算三角形底边的起始位置
                path.moveTo(x, y);  // 顶点
                path.lineTo(x - halfSize, y - triangleSize);  // 左下
                path.lineTo(x + halfSize, y - triangleSize);  // 右下
                break;
            case "right":
                // 计算三角形底边的起始位置
                path.moveTo(x, y);  // 顶点
                path.lineTo(x + triangleSize, y - halfSize);  // 右下
                path.lineTo(x + triangleSize, y + halfSize);  // 右上
                break;
            case "down":
                // 计算三角形底边的起始位置
                path.moveTo(x, y);  // 顶点
                path.lineTo(x - halfSize, y + triangleSize);  // 左上
                path.lineTo(x + halfSize, y + triangleSize);  // 右上
                break;
        }

        path.close();  // 形成封闭路径
        canvas.drawPath(path, paint);  // 绘制实心三角形
    }
}
