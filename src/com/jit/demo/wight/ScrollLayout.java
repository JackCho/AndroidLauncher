package com.jit.demo.wight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.Scroller;

import com.jit.demo.R;
import com.jit.demo.adapter.ScrollAdapter;
import com.jit.demo.adapter.ScrollAdapter.OnDataChangeListener;
import com.jit.demo.model.MoveItem;
import com.jit.demo.util.DensityUtil;
import com.lidroid.xutils.DbUtils;
import com.lidroid.xutils.db.sqlite.WhereBuilder;
import com.lidroid.xutils.exception.DbException;

public class ScrollLayout extends ViewGroup implements OnDataChangeListener {

	//容器的Adapter
	private ScrollAdapter mAdapter;
	
	//左边距
	private int leftPadding = 0;
	//右边距
	private int rightPadding = 0;
	//上边距
	private int topPadding = 0;
	//下边距
	private int bottomPadding = 0;
	
	//每个Item图片宽度的半长，用于松下手指时的动画
	private int halfBitmapWidth;
	//同上
	private int halfBitmapHeight;

	//动态设置行数
	private int rowCount = 1;
	//动态设置列数
	private int colCount = 1;
	//每一页的Item总数
	private int itemPerPage = 1;
	
	//行间距
	private int rowSpace = 0;
	//列间距
	private int colSpace = 0;
	
	//item的宽度
	private int childWidth = 0;
	//item的高度
	private int childHeight = 0;
	
	//手机屏幕宽度
	private int screenWidth = 0;
	//手机屏幕高度
	private int screenHeight = 0;

	//总Item数
	private int totalItem = 0;
	//总页数
	private int totalPage = 0;
	//当前屏数
	private int mCurScreen;
	//默认屏数为0，即第一屏
	private int mDefaultScreen = 0;

	//上次位移滑动到的X坐标位置
	private float mLastMotionX;
	//上次位移滑动到的Y坐标位置
	private float mLastMotionY;
	
	//拖动点的X坐标（加上当前屏数 * screenWidth）
	private int dragPointX;
	//拖动点的Y坐标
	private int dragPointY;
	//X坐标偏移量
	private int dragOffsetX;
	//Y坐标偏移量
	private int dragOffsetY;
	
	//拖拽点的位置编号，每个Item对应一个位置编号，自增
	private int dragPosition = -1;

	//临时交换位置的编号
	private int temChangPosition = -1;

	//window管理器，负责随手势显示拖拽View
	private WindowManager windowManager;
	private WindowManager.LayoutParams windowParams;
	
	//拖拽Item的子View
	private ImageView dragImageView;
	//拖拽View对应的位图
	private Bitmap dragBitmap;
	
	//页面滚动的Scroll管理器
	private Scroller mScroller;

	//三种滑动状态，默认为静止状态
	private int Mode_Free = 0; //静止状态
	private int Mode_Drag = 1; //当前页面下，拖动状态
	private int Mode_Scroll = 2; //跨页面滚动状态
	private int Mode = Mode_Free; 

	//手势落下的X坐标
	private int startX = 0;

	//编辑状态标识
	private boolean isEditting;

	private Context mContext;

	//Container的背景
	private Bitmap background;
	//背景绘制的Paint
	private Paint paint = new Paint(1);
	
	//系列动画执行完成标识的集合
	private HashMap<Integer, Boolean> animationMap = new HashMap<Integer, Boolean>();

	//用来判断滑动到哪一个item的位置
	private Rect frame;

	//页面滑动的监听
	private OnPageChangedListener pageChangedListener;
	//删除或增加页面的监听
	private OnAddOrDeletePage onAddPage;
	//Container编辑模式的监听
	private OnEditModeListener onEditModeListener;

	public ScrollLayout(Context context) {
		super(context);
		init(context);
	}

	public ScrollLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public ScrollLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}
	
	//初始化成员变量，同时设置OnClick监听
	private void init(Context context) {
		this.mContext = context;
		this.mScroller = new Scroller(context);
		
		this.mCurScreen = mDefaultScreen;
		
		this.rightPadding = DensityUtil.dip2px(mContext, 70);
		this.leftPadding = DensityUtil.dip2px(mContext, 10);
		this.topPadding = DensityUtil.dip2px(mContext, 30);
		this.bottomPadding = DensityUtil.dip2px(mContext, 30);
		
		this.colSpace = DensityUtil.dip2px(mContext, 15);
		this.rowSpace = DensityUtil.dip2px(mContext, 15);
		
		if (mAdapter != null)
			refreView();

		this.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showEdit(false);
			}
		});
	}
	
	//添加一个item
	public void addItemView(MoveItem item) {
		mAdapter.add(item);
		this.addView(getView(mAdapter.getCount() - 1));
		showEdit(isEditting);
		requestLayout();
	}

	@Override
	public void addView(View child, int index, LayoutParams params) {
		child.setClickable(true);
		if (child.getVisibility() != View.VISIBLE)
			child.setVisibility(View.VISIBLE);
		super.addView(child, index, params);
		int pages = (int) Math.ceil(getChildCount() * 1.0 / itemPerPage);
		if (pages > totalPage) {
			if (this.onAddPage != null)
				onAddPage.onAddOrDeletePage(totalPage, true);
			totalPage = pages;
		}
	}
	
	//绘制Container所有item
	public void refreView() {
		removeAllViews();
		for (int i = 0; i < mAdapter.getCount(); i++) {
			this.addView(getView(i));
		}
		totalPage = (int) Math.ceil(getChildCount() * 1.0 / itemPerPage);
		requestLayout();
	}

	@Override
	public void removeView(View view) {
		super.removeView(view);
		int pages = (int) Math.ceil(getChildCount() * 1.0 / itemPerPage);
		if (pages < totalPage) {
			if (this.onAddPage != null)
				onAddPage.onAddOrDeletePage(totalPage, false);
			totalPage = pages;
		}
	}

	@Override
	public void removeViewAt(int index) {
		super.removeViewAt(index);
		int pages = (int) Math.ceil(getChildCount() * 1.0 / itemPerPage);
		if (pages < totalPage) {
			totalPage = pages;
			if (this.onAddPage != null)
				onAddPage.onAddOrDeletePage(totalPage, false);
		}
	}
	

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		final int action = ev.getAction();
		final float x = ev.getX();
		final float y = ev.getY();
		int thresholdX = DensityUtil.dip2px(mContext, 8);
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			startX = (int) x;
			if (mScroller.isFinished()) {
				if (!mScroller.isFinished()) {
					mScroller.abortAnimation();
				}
				temChangPosition = dragPosition = pointToPosition((int) x, (int) y);
				dragOffsetX = (int) (ev.getRawX() - x);
				dragOffsetY = (int) (ev.getRawY() - y);

				mLastMotionX = x;
				mLastMotionY = y;
				startX = (int) x;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			int deltaX = (int) (mLastMotionX - x);

			if (IsCanMove(deltaX) && Math.abs(deltaX) > thresholdX && Mode != Mode_Drag) {
				mLastMotionX = x;
				scrollBy(deltaX, 0);
				Mode = Mode_Scroll;
			}

			if (Mode == Mode_Drag) {
				onDrag((int) x, (int) y);
			}
			break;
		case MotionEvent.ACTION_UP:
			float distance = ev.getRawX() - startX;
			if (distance > screenWidth / 6 && mCurScreen > 0
					&& Mode != Mode_Drag) {
				snapToScreen(mCurScreen - 1);
			} else if (distance < -screenWidth / 6
					&& mCurScreen < totalPage - 1 && Mode != Mode_Drag) {
				snapToScreen(mCurScreen + 1);
			} else if (Mode != Mode_Drag) {
				snapToDestination();
			}
			if (Mode == Mode_Drag) {
				stopDrag();
			}
			if (dragImageView != null) {
				animationMap.clear();
				showDropAnimation((int) x, (int) y);
			}
			startX = 0;
			break;
		case MotionEvent.ACTION_CANCEL:
			showEdit(false);
		}
		super.dispatchTouchEvent(ev);
		return true;
	}
	
	//开始拖动
	private void startDrag(Bitmap bm, int x, int y, View itemView) {
		dragPointX = x - itemView.getLeft() + mCurScreen * screenWidth;
		dragPointY = y - itemView.getTop();
		windowParams = new WindowManager.LayoutParams();

		windowParams.gravity = Gravity.TOP | Gravity.LEFT;
		windowParams.x = x - dragPointX + dragOffsetX;
		windowParams.y = y - dragPointY + dragOffsetY;
		windowParams.height = LayoutParams.WRAP_CONTENT;
		windowParams.width = LayoutParams.WRAP_CONTENT;
		windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

		windowParams.format = PixelFormat.TRANSLUCENT;
		windowParams.windowAnimations = 0;
		windowParams.alpha = 0.8f;

		ImageView iv = new ImageView(getContext());
		iv.setImageBitmap(bm);
		dragBitmap = bm;
		windowManager = (WindowManager) getContext().getSystemService(
				Context.WINDOW_SERVICE);
		windowManager.addView(iv, windowParams);
		dragImageView = iv;
		Mode = Mode_Drag;

		halfBitmapWidth = bm.getWidth() / 2;
		halfBitmapHeight = bm.getHeight() / 2;

		for (int i = 0; i < getChildCount(); i++) {
			getChildAt(i).getBackground().setAlpha((int) (0.8f * 255));
		}
	}

	//停止拖动
	private void stopDrag() {
		recoverChildren();
		if (Mode == Mode_Drag) {
			if (getChildAt(dragPosition).getVisibility() != View.VISIBLE)
				getChildAt(dragPosition).setVisibility(View.VISIBLE);
			Mode = Mode_Free;
			Log.e("test", "scroll menu move");
			mContext.sendBroadcast(new Intent("com.stg.menu_move"));
		}
	}

	//使用Map集合记录，防止动画执行混乱
	private class NotifyDataSetListener implements AnimationListener {
		private int movedPosition;

		public NotifyDataSetListener(int primaryPosition) {
			this.movedPosition = primaryPosition;
		}

		@Override
		public void onAnimationEnd(Animation animation) {
			if (animationMap.containsKey(movedPosition)) {
				// remove from map when end
				animationMap.remove(movedPosition);
			}
		}

		@Override
		public void onAnimationRepeat(Animation animation) {
		}

		@Override
		public void onAnimationStart(Animation animation) {
			// put into map when start
			animationMap.put(movedPosition, true);
		}
	}

	//返回滑动的位移动画，比较复杂，有兴趣的可以看看
	private Animation animationPositionToPosition(int oldP, int newP,
			boolean isCrossScreen, boolean isForward) {
		PointF oldPF = positionToPoint2(oldP);
		PointF newPF = positionToPoint2(newP);

		TranslateAnimation animation = null;

		// when moving forward across pages,the first item of the new page moves
		// backward
		if (oldP != 0 && (oldP + 1) % itemPerPage == 0 && isForward) {
			animation = new TranslateAnimation(screenWidth - oldPF.x, 0,
					DensityUtil.dip2px(mContext, 25) - screenHeight, 0);
			animation.setDuration(800);
		}
		// when moving backward across pages,the last item of the new page moves
		// forward
		else if (oldP != 0 && oldP % itemPerPage == 0 && isCrossScreen && !isForward) {
			animation = new TranslateAnimation(newPF.x - screenWidth, 0,
					screenHeight - DensityUtil.dip2px(mContext, 25), 0);
			animation.setDuration(800);
		}
		// regular animation between two neighbor items
		else {
			animation = new TranslateAnimation(newPF.x - oldPF.x, 0, newPF.y
					- oldPF.y, 0);
			animation.setDuration(500);
		}
		animation.setFillAfter(true);
		animation.setAnimationListener(new NotifyDataSetListener(oldP));

		return animation;
	}
	

	//滑动合法性的判断，防止滑动到空白区域
	private boolean IsCanMove(int deltaX) {
		if (getScrollX() <= 0 && deltaX < 0) {
			return false;
		}
		if (getScrollX() >= (totalPage - 1) * screenWidth && deltaX > 0) {
			return false;
		}
		return true;
	}

	//判断滑动的一系列动画是否有冲突
	private boolean isMovingFastConflict(int moveNum) {
		int itemsMoveNum = Math.abs(moveNum);
		int temp = dragPosition;
		for (int i = 0; i < itemsMoveNum; i++) {
			int holdPosition = moveNum > 0 ? temp + 1 : temp - 1;
			if (animationMap.containsKey(holdPosition)) {
				return true;
			}
			temp = holdPosition;
		}
		return false;
	}

	//执行位置动画
	private void movePostionAnimation(int oldP, int newP) {
		int moveNum = newP - oldP;
		boolean isCrossScreen = false;
		boolean isForward = false;
		if (moveNum != 0 && !isMovingFastConflict(moveNum)) {
			int absMoveNum = Math.abs(moveNum);
			for (int i = Math.min(oldP, newP) + 1; i <= Math.max(oldP, newP); i++) {
				if (i % 8 == 0) {
					isCrossScreen = true;
				}
			}
			if (isCrossScreen) {
				isForward = moveNum < 0 ? false : true;
			}
			for (int i = 0; i < absMoveNum; i++) {
				int holdPosition = (moveNum > 0) ? oldP + 1 : oldP - 1;
				View view = getChildAt(holdPosition);
				if (view != null) {
					view.startAnimation(animationPositionToPosition(oldP,
							holdPosition, isCrossScreen, isForward));
				}
				oldP = holdPosition;
			}
		}
	}
	
	//滑动过程中，使所有的item暗掉
	private void fadeChildren() {
		final int count = getChildCount() - 1;
		for (int i = count; i >= 0; i--) {
			View child = getChildAt(i);
			child.getBackground().setAlpha(180);
		}
	}

	//滑动停止后，恢复item的透明度
	private void recoverChildren() {
		final int count = getChildCount() - 1;
		for (int i = count; i >= 0; i--) {
			final View child = getChildAt(i);
			// child.setAlpha(1.0f);
			child.getBackground().setAlpha(255);
			Drawable drawable = child.getBackground();
			if (drawable != null) {
				child.getBackground().setAlpha(255);
			}
		}
	}
	
	public int getChildIndex(View view) {
		if (view != null && view.getParent() instanceof ScrollLayout) {
			final int childCount = ((ScrollLayout) view.getParent()).getChildCount();
			for (int i = 0; i < childCount; i++) {
				if (view == ((ScrollLayout) view.getParent()).getChildAt(i)) {
					return i;
				}
			}
		}
		return -1;
	}
	
	//获取特定position下的item View
	private View getView(final int position) {
		View view = null;
		if (mAdapter != null) {
			view = mAdapter.getView(position);
			view.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					Log.e("test", "onLongClick");
//					if (Mode != Mode_Scroll) {
						return onItemLongClick(v);
//					}
//					return false;
				}
			});

		}
		return view;
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
			postInvalidate();
		}
	}

	//Container背景滑动的实现，下面是计算公式
	// int w = x * ((width - n) / (totalPage - 1)) / getWidth();
	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (this.background != null) {
			int width = this.background.getWidth();
			int height = this.background.getHeight();
			int x = getScrollX();
			int n = height * getWidth() / getHeight();
			int w;
			if (totalPage == 1) {
				w = x * (width - n) / 1 / getWidth();
			} else {
				w = x * ((width - n) / (totalPage - 1)) / getWidth();
			}
			canvas.drawBitmap(this.background, new Rect(w, 0, n + w, height), new Rect(
					x, 0, x + getWidth(), getHeight()), this.paint);
		}
		super.dispatchDraw(canvas);
	}

	//获取当前Container状态下，所有的item
	public List<MoveItem> getAllMoveItems() {
		List<MoveItem> items = new ArrayList<MoveItem>();
		int count = getChildCount();
		MoveItem item = null;
		for (int i = 0; i < count; i++) {
			item = (MoveItem) getChildAt(i).getTag();
			items.add(item);
		}
		return items;
	}

	public Bitmap getBg() {
		return background;
	}

	public int getBottomPadding() {
		return bottomPadding;
	}

	public int getColCount() {
		return colCount;
	}

	public int getColSpace() {
		return colSpace;
	}

	public int getCurrentPage() {
		return mCurScreen;
	}

	public int getLeftPadding() {
		return leftPadding;
	}

	public OnAddOrDeletePage getOnCaculatePage() {
		return onAddPage;
	}

	public OnEditModeListener getOnEditModeListener() {
		return onEditModeListener;
	}

	public OnPageChangedListener getOnPageChangedListener() {
		return pageChangedListener;
	}

	public int getRightPadding() {
		return rightPadding;
	}

	public int getRowCount() {
		return rowCount;
	}

	public int getRowSpace() {
		return rowSpace;
	}

	public ScrollAdapter getSaAdapter() {
		return mAdapter;
	}

	public int getTopPadding() {
		return topPadding;
	}

	public int getTotalItem() {
		return totalItem;
	}

	public int getTotalPage() {
		return totalPage;
	}



	@Override
	public void ondataChange() {
		refreView();
	}

	//根据手势绘制不断变化位置的dragView
	private void onDrag(int x, int y) {
		if (dragImageView != null) {
			windowParams.alpha = 0.8f;
			windowParams.x = x - dragPointX + dragOffsetX;
			windowParams.y = y - dragPointY + dragOffsetY;
			windowManager.updateViewLayout(dragImageView, windowParams);
		}
		int tempPosition = pointToPosition(x, y);
		if (tempPosition != -1) {
			dragPosition = tempPosition;
		}
		View view = getChildAt(temChangPosition);
		if (view == null) {
			stopDrag();
			return;
		}
		view.setVisibility(View.INVISIBLE);
		if (temChangPosition != dragPosition) {
			View dragView = getChildAt(temChangPosition);
			movePostionAnimation(temChangPosition, dragPosition);
			removeViewAt(temChangPosition);
			addView(dragView, dragPosition);
			getChildAt(dragPosition).setVisibility(View.INVISIBLE);
			this.getSaAdapter().exchange(temChangPosition, dragPosition);
			temChangPosition = dragPosition;
		}

		if (x > getRight() - DensityUtil.dip2px(mContext, 25)
				&& mCurScreen < totalPage - 1 && mScroller.isFinished()
				&& x - startX > 10) {
			snapToScreen(mCurScreen + 1, false);
		} else if (x - getLeft() < DensityUtil.dip2px(mContext, 35)
				&& mCurScreen > 0 && mScroller.isFinished() && x - startX < -10) {
			snapToScreen(mCurScreen - 1, false);
		}

	}

	public boolean onItemLongClick(View v) {
		if (mScroller.isFinished()) {
			v.destroyDrawingCache();
			v.setDrawingCacheEnabled(true);
			fadeChildren();
			if (onEditModeListener != null)
				onEditModeListener.onEdit();
			Bitmap bm = Bitmap.createBitmap(v.getDrawingCache());
			showEdit(true);
			v.setVisibility(View.GONE);
			startDrag(bm, (int) (mLastMotionX), (int) (mLastMotionY), v);
			return true;
		}
		return false;

	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View childView = getChildAt(i);
			if (childView.getVisibility() != View.GONE) {
				childWidth = childView.getMeasuredWidth();
				childHeight = childView.getMeasuredHeight();
				int page = i / itemPerPage;

				int row = i / colCount % rowCount;
				int col = i % colCount;
				int left = leftPadding + page * screenWidth + col
						* (colSpace + childWidth);
				int top = topPadding + row * (rowSpace + childHeight);

				childView.layout(left, top, left + childWidth,
						top + childView.getMeasuredHeight());
			}
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		final int width = MeasureSpec.getSize(widthMeasureSpec);
		MeasureSpec.getMode(widthMeasureSpec);

		final int height = MeasureSpec.getSize(heightMeasureSpec);
		MeasureSpec.getMode(heightMeasureSpec);

		screenWidth = width;
		screenHeight = height;
		int usedWidth = width - leftPadding - rightPadding - (colCount - 1)
				* colSpace;
		int usedheight = ((height - topPadding - bottomPadding - (rowCount - 1)
				* rowSpace));
		int childWidth = usedWidth / colCount;
		int childHeight = usedheight / rowCount;
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			int childWidthSpec = getChildMeasureSpec(
					MeasureSpec
							.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
					20, childWidth);
			int childHeightSpec = getChildMeasureSpec(
					MeasureSpec.makeMeasureSpec(childHeight,
							MeasureSpec.EXACTLY), 20, childHeight);
			child.measure(childWidthSpec, childHeightSpec);
		}
		scrollTo(mCurScreen * width, 0);
	}

	//根据坐标，判断当前item所属的位置，即编号
	public int pointToPosition(int x, int y) {
		int locX = x + mCurScreen * getWidth();

		if (frame == null)
			frame = new Rect();
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; i--) {
			final View child = getChildAt(i);
			child.getHitRect(frame);
			if (frame.contains(locX, y)) {
				return i;
			}
		}
		return -1;
	}

	//item编号对应的左上角坐标
	public PointF positionToPoint1(int position) {
		PointF point = new PointF();

		int page = position / itemPerPage;
		int row = position / colCount % rowCount;
		int col = position % colCount;
		int left = leftPadding + page * screenWidth + col
				* (colSpace + childWidth);
		int top = topPadding + row * (rowSpace + childHeight);

		point.x = left;
		point.y = top;
		return point;

	}

	public PointF positionToPoint2(int position) {
		PointF point = new PointF();

		int row = position / colCount % rowCount;
		int col = position % colCount;
		int left = leftPadding + col * (colSpace + childWidth);
		int top = topPadding + row * (rowSpace + childHeight);

		point.x = left;
		point.y = top;
		return point;

	}

	public void setBackGroud(Bitmap paramBitmap) {
		this.background = paramBitmap;
		this.paint.setFilterBitmap(true);
	}

	public void setBottomPadding(int bottomPadding) {
		this.bottomPadding = bottomPadding;
	}

	public void setColCount(int colCount) {
		this.colCount = colCount;
		this.itemPerPage = this.colCount * this.rowCount;
	}

	public boolean isEditting() {
		return isEditting;
	}

	public void setColSpace(int colSpace) {
		this.colSpace = colSpace;
	}

	public void setLeftPadding(int leftPadding) {
		this.leftPadding = leftPadding;
	}

	public void setOnAddPage(OnAddOrDeletePage onAddPage) {
		this.onAddPage = onAddPage;
	}

	public void setOnEditModeListener(OnEditModeListener onEditModeListener) {
		this.onEditModeListener = onEditModeListener;
	}

	public void setOnPageChangedListener(
			OnPageChangedListener pageChangedListener) {
		this.pageChangedListener = pageChangedListener;
	}

	public void setRightPadding(int rightPadding) {
		this.rightPadding = rightPadding;
	}

	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
		this.itemPerPage = this.colCount * this.rowCount;
	}

	public void setRowSpace(int rowSpace) {
		this.rowSpace = rowSpace;
	}

	public void setSaAdapter(ScrollAdapter saAdapter) {
		this.mAdapter = saAdapter;
		this.mAdapter.setOnDataChangeListener(this);
	}

	public void setTopPadding(int topPadding) {
		this.topPadding = topPadding;
	}

	public void setTotalItem(int totalItem) {
		this.totalItem = totalItem;
	}

	//执行松手动画
	private void showDropAnimation(int x, int y) {
		ViewGroup moveView = (ViewGroup) getChildAt(dragPosition);
		TranslateAnimation animation = new TranslateAnimation(x
				- halfBitmapWidth - moveView.getLeft(), 0, y - halfBitmapHeight
				- moveView.getTop(), 0);
		animation.setFillAfter(false);
		animation.setDuration(300);
		moveView.setAnimation(animation);
		windowManager.removeView(dragImageView);
		dragImageView = null;

		if (dragBitmap != null) {
			dragBitmap = null;
		}

		for (int i = 0; i < getChildCount(); i++) {
			getChildAt(i).clearAnimation();
		}
	}

	public void showEdit(boolean isEdit) {
		isEditting = isEdit;
		int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			ImageView iv = (ImageView) child.findViewById(R.id.delete_iv);
			iv.setTag(child.getTag());
			iv.setVisibility(isEdit == true ? View.VISIBLE : View.GONE);
			if (isEdit) {
				iv.setOnClickListener(new DelItemClick(i));
			}
		}

		if (isEdit == false) {
			int pages = (int) Math.ceil(getChildCount() * 1.0 / itemPerPage);
			if (pages < totalPage) {
				totalPage = pages;
				if (this.onAddPage != null)
					onAddPage.onAddOrDeletePage(totalPage + 1, false);
			}
		}
	}

	//滚屏
	public void snapToDestination() {
		final int screenWidth = getWidth();
		final int destScreen = (getScrollX() + screenWidth / 2) / screenWidth;
		if (destScreen >= 0 && destScreen < totalPage) {
			snapToScreen(destScreen);
		}
	}

	public void snapToScreen(int whichScreen) {
		snapToScreen(whichScreen, true);
	}

	public void snapToScreen(int whichScreen, boolean isFast) {
		// get the valid layout page
		whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));
		if (getScrollX() != (whichScreen * getWidth())) {

			final int delta = whichScreen * getWidth() - getScrollX();

			if (pageChangedListener != null)
				pageChangedListener.onPage2Other(mCurScreen, whichScreen);

			if (!isFast)
				mScroller.startScroll(getScrollX(), 0, delta, 0, 800);
			else
				mScroller.startScroll(getScrollX(), 0, delta, 0, 500);
			mCurScreen = whichScreen;
			requestLayout();
			invalidate(); // Redraw the layout
		}
	}

	/**
	 * 删除按钮的功能处理
	 *
	 */
	private final class DelItemClick implements OnClickListener {
		int deletePostion;

		public DelItemClick(int deletePostion) {
			this.deletePostion = deletePostion;
		}

		@Override
		public void onClick(View v) {
			if (v.getParent() != null) {
				if (mCurScreen < totalPage - 1) {

				}
				movePostionAnimation(deletePostion, getChildCount() - 1);
				removeView((ViewGroup) (v.getParent()));
				mAdapter.delete(deletePostion);
				MoveItem item = (MoveItem) v.getTag();
				DbUtils utils = DbUtils.create(mContext);
				try {
					utils.delete(MoveItem.class,WhereBuilder.b("mid", "=", item.getMid()));
				} catch (DbException e) {
					e.printStackTrace();
				}
				showEdit(false);
				showEdit(true);

				//如果删除后少了一屏，则移动到前一屏，并进行页面刷新
				if (getChildCount() % itemPerPage == 0) {
					snapToScreen(totalPage - 1);
				}
			}
		}
	}
	
	public interface SAdapter {
		void exchange(int oldPosition, int newPositon);
		int getCount();
		View getView(int position);
	}
	
	public interface OnAddOrDeletePage {
		void onAddOrDeletePage(int page, boolean isAdd);
	}

	public interface OnEditModeListener {
		void onEdit();
	}

	public interface OnPageChangedListener {
		void onPage2Other(int n1, int n2);
	}

}
