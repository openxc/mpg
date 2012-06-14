package com.ford.openxc.mpg;

import android.content.Context;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridView;

public class ViewPagerAdapter extends PagerAdapter  {

    private Context cxt;
    private LayoutInflater inflater;

    ViewPagerAdapter(Context context) {
        cxt = context;
        inflater = (LayoutInflater) cxt.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
  @Override
    public int getCount() {
            return 3;
    }

/**
 * Create the page for the given position.  The adapter is responsible
 * for adding the view to the container given here, although it only
 * must ensure this is done by the time it returns from
 * {@link #finishUpdate()}.
 *
 * @param container The containing View in which the page will be shown.
 * @param position The page position to be instantiated.
 * @return Returns an Object representing the new page.  This does not
 * need to be a View, but can be some other container of the page.
 */
    @Override
    public Object instantiateItem(View collection, int position) {
        GridView v = (GridView) inflater.inflate(R.layout.grid, null);
      v.setAdapter(new ImageAdapter(cxt));
      ((ViewPager) collection).addView(v,0);

      return v;
    }

/**
 * Remove a page for the given position.  The adapter is responsible
 * for removing the view from its container, although it only must ensure
 * this is done by the time it returns from {@link #finishUpdate()}.
 *
 * @param container The containing View from which the page will be removed.
 * @param position The page position to be removed.
 * @param object The same object that was returned by
 * {@link #instantiateItem(View, int)}.
 */
    @Override
    public void destroyItem(View collection, int position, Object view) {
            ((ViewPager) collection).removeView((GridView) view);
    }



    @Override
    public boolean isViewFromObject(View view, Object object) {
           return view==((GridView)object);
    }


/**
 * Called when the a change in the shown pages has been completed.  At this
 * point you must ensure that all of the pages have actually been added or
 * removed from the container as appropriate.
 * @param container The containing View which is displaying this adapter's
 * page views.
 */
    @Override
    public void finishUpdate(View arg0) {}


    @Override
    public void restoreState(Parcelable arg0, ClassLoader arg1) {}

    @Override
    public Parcelable saveState() {
            return null;
    }

    @Override
    public void startUpdate(View arg0) {}

  @Override
  public String getTitle(int position) {

      return "TITLE"+position;
  }
}
