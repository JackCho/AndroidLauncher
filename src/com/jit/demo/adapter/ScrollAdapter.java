package com.jit.demo.adapter;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import com.jit.demo.R;
import com.jit.demo.model.MoveItem;
import com.jit.demo.wight.ScrollLayout.SAdapter;

@SuppressLint("UseSparseArrays")
public class ScrollAdapter implements SAdapter {

	private Context mContext;
	private LayoutInflater mInflater;
	
	private List<MoveItem> mList;
	private HashMap<Integer,SoftReference<Drawable>> mCache;
	
	public ScrollAdapter(Context context, List<MoveItem> list) {
		
		this.mContext = context;
		this.mInflater = LayoutInflater.from(context);
		
		this.mList = list;
	    this.mCache = new HashMap<Integer, SoftReference<Drawable>>();
	}

	@Override
	public View getView(int position) {
		View view = null;
		if (position < mList.size()) {
			MoveItem moveItem = mList.get(position);
			view = mInflater.inflate(R.layout.item, null);
			ImageView iv = (ImageView) view.findViewById(R.id.content_iv);
			StateListDrawable states = new StateListDrawable();
			int imgUrl = moveItem.getImgurl();
			int imgUrlDown = moveItem.getImgdown();
			
			Drawable pressed = null;
			Drawable normal = null;
			
			SoftReference<Drawable> p = mCache.get(imgUrlDown);
			if (p != null) {
				pressed = p.get();
			}
			
			SoftReference<Drawable> n = mCache.get(imgUrl);
			if (n != null) {
				normal = n.get();
			}
			
			if (pressed == null) {
				pressed = mContext.getResources().getDrawable(imgUrlDown);
				mCache.put(imgUrlDown, new SoftReference<Drawable>(pressed));
			}
			
			if (normal == null) {
				normal = mContext.getResources().getDrawable(imgUrl);
				mCache.put(imgUrl, new SoftReference<Drawable>(normal));
			}
			
			states.addState(new int[] {android.R.attr.state_pressed},pressed);
			states.addState(new int[] {android.R.attr.state_focused},pressed);
			states.addState(new int[] { }, normal);
			
			iv.setImageDrawable(states);
			view.setTag(moveItem);
		}
		return view;
	}
	
	@Override
	public int getCount() {
		return mList.size();
	}

	@Override
	public void exchange(int oldPosition, int newPositon) {
		MoveItem item = mList.get(oldPosition);
		mList.remove(oldPosition);
		mList.add(newPositon, item);
	}

	private OnDataChangeListener dataChangeListener = null;

	public interface OnDataChangeListener {
		void ondataChange();

	}

	public OnDataChangeListener getOnDataChangeListener() {
		return dataChangeListener;
	}

	public void setOnDataChangeListener(OnDataChangeListener dataChangeListener) {
		this.dataChangeListener = dataChangeListener;
	}

	public void delete(int position) {
		if (position < getCount()) {
			mList.remove(position);
		}
	}

	public void add(MoveItem item) {
		mList.add(item);
	}

	public MoveItem getMoveItem(int position) {
		return mList.get(position);
	}
	
	public void recycleCache() {
		if (mCache != null) {
			Set<Integer> keys = mCache.keySet();
			for (Iterator<Integer> it = keys.iterator(); it.hasNext();) {
				Integer key = it.next();
				SoftReference<Drawable> reference = mCache.get(key);
				if (reference != null) {
					reference.clear();
				}
			}
			mCache.clear();
			mCache = null;
		}
	}
}
