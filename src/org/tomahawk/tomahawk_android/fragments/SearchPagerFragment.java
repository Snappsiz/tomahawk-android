/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2014, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.fragments;

import com.google.common.collect.Sets;

import org.tomahawk.libtomahawk.collection.Album;
import org.tomahawk.libtomahawk.collection.Artist;
import org.tomahawk.libtomahawk.collection.Image;
import org.tomahawk.libtomahawk.infosystem.InfoRequestData;
import org.tomahawk.libtomahawk.infosystem.InfoSystem;
import org.tomahawk.libtomahawk.infosystem.User;
import org.tomahawk.libtomahawk.resolver.PipeLine;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.utils.FragmentInfo;
import org.tomahawk.tomahawk_android.utils.ThreadManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SearchPagerFragment extends PagerFragment {

    private String mCurrentQueryString;

    protected final Set<Query> mCorrespondingQueries
            = Sets.newSetFromMap(new ConcurrentHashMap<Query, Boolean>());

    private final ArrayList<String> mAlbumIds = new ArrayList<>();

    private final ArrayList<String> mArtistIds = new ArrayList<>();

    private final ArrayList<String> mSongIds = new ArrayList<>();

    private final ArrayList<String> mUserIds = new ArrayList<>();

    private Image mContentHeaderImage;

    private SearchFragmentReceiver mSearchFragmentReceiver;

    /**
     * Handles incoming broadcasts.
     */
    private class SearchFragmentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                boolean noConnectivity =
                        intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                if (!noConnectivity) {
                    resolveFullTextQuery(mCurrentQueryString);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(PipeLine.ResultsEvent event) {
        if (mCorrespondingQueries.contains(event.mQuery)) {
            mSongIds.clear();
            if (event.mQuery != null) {
                for (Query q : event.mQuery.getTrackQueries()) {
                    mSongIds.add(q.getCacheKey());
                }
            }
            updatePager();
        }
    }

    /**
     * Restore the {@link String} inside the search {@link android.widget.TextView}. Either through
     * the savedInstanceState {@link Bundle} or through the a {@link Bundle} provided in the
     * Arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null
                && savedInstanceState.containsKey(TomahawkFragment.QUERY_STRING)
                && savedInstanceState.getString(TomahawkFragment.QUERY_STRING) != null) {
            mCurrentQueryString = savedInstanceState.getString(
                    TomahawkFragment.QUERY_STRING);
        }
    }


    /**
     * Called, when this {@link SearchPagerFragment}'s {@link android.view.View} has been created
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int initialPage = -1;
        if (getArguments() != null) {
            if (getArguments().containsKey(TomahawkFragment.CONTAINER_FRAGMENT_PAGE)) {
                initialPage = getArguments().getInt(TomahawkFragment.CONTAINER_FRAGMENT_PAGE);
            }
            if (getArguments().containsKey(TomahawkFragment.QUERY_STRING)
                    && getArguments().getString(TomahawkFragment.QUERY_STRING) != null) {
                mCurrentQueryString = getArguments().getString(
                        TomahawkFragment.QUERY_STRING);
            }
        }

        // If we have restored a CurrentQueryString, start searching, so that we show the proper
        // results again
        if (mCurrentQueryString != null) {
            resolveFullTextQuery(mCurrentQueryString);
            getActivity().setTitle(mCurrentQueryString);
        }

        showContentHeader(mContentHeaderImage);

        updatePager(initialPage);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Initialize and register Receiver
        if (mSearchFragmentReceiver == null) {
            mSearchFragmentReceiver = new SearchFragmentReceiver();
            IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            getActivity().registerReceiver(mSearchFragmentReceiver, intentFilter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        for (Query query : mCorrespondingQueries) {
            if (ThreadManager.getInstance().stop(query)) {
                mCorrespondingQueries.remove(query);
            }
        }

        if (mSearchFragmentReceiver != null) {
            getActivity().unregisterReceiver(mSearchFragmentReceiver);
            mSearchFragmentReceiver = null;
        }
    }

    /**
     * Save the {@link String} inside the search {@link android.widget.TextView}.
     */
    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putString(TomahawkFragment.QUERY_STRING, mCurrentQueryString);
        super.onSaveInstanceState(out);
    }

    private void updatePager() {
        updatePager(-1);
    }

    private void updatePager(int initialPage) {

        List<FragmentInfoList> fragmentInfoLists = new ArrayList<>();
        FragmentInfoList fragmentInfoList = new FragmentInfoList();
        FragmentInfo fragmentInfo = new FragmentInfo();
        fragmentInfo.mClass = ArtistsFragment.class;
        fragmentInfo.mTitle = getString(R.string.artists);
        fragmentInfo.mBundle = getChildFragmentBundle();
        if (mArtistIds != null) {
            fragmentInfo.mBundle
                    .putStringArrayList(TomahawkFragment.ARTISTARRAY, mArtistIds);
        }
        fragmentInfoList.addFragmentInfo(fragmentInfo);
        fragmentInfoLists.add(fragmentInfoList);

        fragmentInfoList = new FragmentInfoList();
        fragmentInfo = new FragmentInfo();
        fragmentInfo.mClass = AlbumsFragment.class;
        fragmentInfo.mTitle = getString(R.string.albums);
        fragmentInfo.mBundle = getChildFragmentBundle();
        if (mAlbumIds != null) {
            fragmentInfo.mBundle
                    .putStringArrayList(TomahawkFragment.ALBUMARRAY, mAlbumIds);
        }
        fragmentInfoList.addFragmentInfo(fragmentInfo);
        fragmentInfoLists.add(fragmentInfoList);

        fragmentInfoList = new FragmentInfoList();
        fragmentInfo = new FragmentInfo();
        fragmentInfo.mClass = TracksFragment.class;
        fragmentInfo.mTitle = getString(R.string.songs);
        fragmentInfo.mBundle = getChildFragmentBundle();
        if (mSongIds != null) {
            fragmentInfo.mBundle
                    .putStringArrayList(TomahawkFragment.QUERYARRAY, mSongIds);
        }
        fragmentInfoList.addFragmentInfo(fragmentInfo);
        fragmentInfoLists.add(fragmentInfoList);

        fragmentInfoList = new FragmentInfoList();
        fragmentInfo = new FragmentInfo();
        fragmentInfo.mClass = UsersFragment.class;
        fragmentInfo.mTitle = getString(R.string.users);
        fragmentInfo.mBundle = getChildFragmentBundle();
        if (mUserIds != null) {
            fragmentInfo.mBundle
                    .putStringArrayList(TomahawkFragment.USERARRAY, mUserIds);
        }
        fragmentInfoList.addFragmentInfo(fragmentInfo);
        fragmentInfoLists.add(fragmentInfoList);

        setupPager(fragmentInfoLists, initialPage, null);
    }

    /**
     * Invoke the resolving process with the given fullTextQuery {@link String}
     */
    public void resolveFullTextQuery(String fullTextQuery) {
        ((TomahawkMainActivity) getActivity()).closeDrawer();
        mSongIds.clear();
        mAlbumIds.clear();
        mArtistIds.clear();
        mUserIds.clear();
        mCurrentQueryString = fullTextQuery;
        mCorrespondingRequestIds.clear();
        mCorrespondingRequestIds.add(InfoSystem.getInstance().resolve(fullTextQuery));
        Query query = PipeLine.getInstance().resolve(fullTextQuery, false);
        if (query != null) {
            mCorrespondingQueries.clear();
            mCorrespondingQueries.add(query);
        }
    }

    @Override
    protected void onInfoSystemResultsReported(InfoRequestData infoRequestData) {
        for (Artist artist : infoRequestData.getResultList(Artist.class)) {
            if (mContentHeaderImage == null && artist.getImage() != null) {
                mContentHeaderImage = artist.getImage();
                showContentHeader(mContentHeaderImage);
            }
            mArtistIds.add(artist.getCacheKey());
        }
        for (Album album : infoRequestData.getResultList(Album.class)) {
            if (mContentHeaderImage == null && album.getImage() != null) {
                mContentHeaderImage = album.getImage();
                showContentHeader(mContentHeaderImage);
            }
            mAlbumIds.add(album.getCacheKey());
        }
        for (User user : infoRequestData.getResultList(User.class)) {
            if (mContentHeaderImage == null && user.getImage() != null) {
                mContentHeaderImage = user.getImage();
                showContentHeader(mContentHeaderImage);
            }
            mUserIds.add(user.getCacheKey());
        }
        updatePager();
    }
}
