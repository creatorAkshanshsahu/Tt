package com.livetvbox.nativev1;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends Activity {
    private static final String API="https://livetgtv.lovable.app/api/public/channels";
    private final ExecutorService exec=Executors.newSingleThreadExecutor();
    private final ArrayList<Channel> all=new ArrayList<>(), shown=new ArrayList<>();
    private ChannelAdapter adapter;
    private TextView status;
    private EditText search;
    private RecyclerView grid;
    private FrameLayout playerLayer;
    private PlayerView playerView;
    private ExoPlayer player;

    @Override public void onCreate(@Nullable Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        build();
        loadCatalogue();
    }

    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView tv(String s,float size,int color){
        TextView t=new TextView(this); t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }

    private void build(){
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.rgb(9,11,15));
        LinearLayout browse=new LinearLayout(this); browse.setOrientation(LinearLayout.VERTICAL);
        browse.setPadding(dp(22),dp(18),dp(22),dp(10));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("LIVE TV",28,Color.WHITE);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));

        search=new EditText(this);search.setSingleLine(true);search.setHint("Search channel");
        search.setTextColor(Color.WHITE);search.setHintTextColor(Color.rgb(145,153,166));
        search.setTextSize(17);search.setPadding(dp(14),0,dp(14),0);search.setBackgroundColor(Color.rgb(28,33,42));
        head.addView(search,new LinearLayout.LayoutParams(dp(300),dp(48)));
        Button refresh=new Button(this);refresh.setText("REFRESH");refresh.setOnClickListener(v->loadCatalogue());
        head.addView(refresh,new LinearLayout.LayoutParams(dp(120),dp(48)));
        browse.addView(head);

        status=tv("Loading channels...",15,Color.rgb(165,174,187));status.setPadding(0,dp(6),0,dp(8));browse.addView(status);
        grid=new RecyclerView(this);grid.setLayoutManager(new GridLayoutManager(this,5));
        adapter=new ChannelAdapter();grid.setAdapter(adapter);
        browse.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(browse,new FrameLayout.LayoutParams(-1,-1));

        search.setOnEditorActionListener((v,a,e)->{filter(search.getText().toString());return false;});
        search.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_DOWN&&e.getAction()==KeyEvent.ACTION_DOWN&&grid.getChildCount()>0){grid.getChildAt(0).requestFocus();return true;}return false;});

        playerLayer=new FrameLayout(this);playerLayer.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);playerView.setUseController(true);
        playerLayer.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        playerLayer.setVisibility(View.GONE);root.addView(playerLayer,new FrameLayout.LayoutParams(-1,-1));
        playerLayer.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_BACK&&e.getAction()==KeyEvent.ACTION_UP){closePlayer();return true;}return false;});
        setContentView(root);
    }

    private void loadCatalogue(){
        status.setText("Loading complete channel catalogue...");
        exec.execute(()->{
            try{
                String body=get(API);
                ArrayList<Channel> p=parseCatalogue(body);
                runOnUiThread(()->{
                    all.clear();all.addAll(p);filter("");
                    status.setText(p.size()+" channels loaded");
                    if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();
                });
            }catch(Exception e){runOnUiThread(()->status.setText("Catalogue error: "+e.getMessage()));}
        });
    }

    private void filter(String q){
        shown.clear();String x=q==null?"":q.trim().toLowerCase(Locale.US);
        for(Channel c:all)if(x.length()==0||c.name.toLowerCase(Locale.US).contains(x)||c.category.toLowerCase(Locale.US).contains(x))shown.add(c);
        adapter.notifyDataSetChanged();if(status!=null)status.setText(shown.size()+" channels");
    }

    private String get(String u)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");
        InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();
        BufferedReader r=new BufferedReader(new InputStreamReader(in));StringBuilder b=new StringBuilder();String l;
        while((l=r.readLine())!=null)b.append(l);r.close();c.disconnect();return b.toString();
    }

    private ArrayList<Channel> parseCatalogue(String s)throws Exception{
        ArrayList<Channel> out=new ArrayList<>();JSONObject o=new JSONObject(s);JSONArray a=o.optJSONArray("channels");
        if(a==null)return out;
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;
            String id=x.optString("id",""),name=x.optString("name","Channel "+i),cat=x.optString("category","Other"),logo=x.optString("logo","");
            if(id.length()>0)out.add(new Channel(id,name,cat,logo));
        }return out;
    }

    private void resolveAndPlay(Channel ch){
        status.setText("Getting fresh stream for "+ch.name+"...");
        exec.execute(()->{
            try{
                // Tokens are short-lived, so this request is made immediately before playback.
                String body=get(API+"/"+URLEncoder.encode(ch.id,"UTF-8"));
                JSONObject o=new JSONObject(body);
                JSONArray src=o.optJSONArray("sources");
                if(src==null||src.length()==0)throw new Exception("No source returned");
                String manifest=src.optString(0,"");
                if(manifest.length()==0)throw new Exception("Empty manifest");

                JSONObject drm=o.optJSONObject("drm");
                final String keyId=drm==null?"":drm.optString("keyId","");
                final String key=drm==null?"":drm.optString("key","");

                runOnUiThread(()->startPlayback(manifest,ch.name,keyId,key));
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Playback setup failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        });
    }

    private void startPlayback(String manifest,String name,String keyId,String key){
        if(player!=null)player.release();
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem.Builder mb=new MediaItem.Builder().setUri(Uri.parse(manifest))
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(name).build());

        if(keyId.length()>0&&key.length()>0){
            // Media3 ClearKey expects a JSON key request for DRM playback.
            String clearKeyJson="{\"keys\":[{\"k\":\""+key+"\",\"kid\":\""+keyId+"\",\"kty\":\"oct\"}],\"type\":\"temporary\"}";
            mb.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setKeySetId(clearKeyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .build());
        }

        player.setMediaItem(mb.build());
        player.prepare();player.play();
        playerLayer.setVisibility(View.VISIBLE);playerLayer.requestFocus();
    }

    private void closePlayer(){
        if(player!=null){player.stop();player.release();player=null;}
        playerView.setPlayer(null);playerLayer.setVisibility(View.GONE);
        if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();
    }

    @Override public void onBackPressed(){if(playerLayer.getVisibility()==View.VISIBLE)closePlayer();else super.onBackPressed();}
    @Override protected void onDestroy(){if(player!=null)player.release();exec.shutdownNow();super.onDestroy();}

    static class Channel{String id,name,category,logo;Channel(String i,String n,String c,String l){id=i;name=n;category=c;logo=l;}}

    private class ChannelAdapter extends RecyclerView.Adapter<Holder>{
        @Override public Holder onCreateViewHolder(android.view.ViewGroup p,int v){
            TextView t=tv("",18,Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(16),dp(10),dp(16),dp(10));
            t.setFocusable(true);t.setClickable(true);t.setBackgroundResource(com.livetvbox.nativev1.R.drawable.card_bg);
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(74));lp.setMargins(dp(5),dp(5),dp(5),dp(5));t.setLayoutParams(lp);return new Holder(t);
        }
        @Override public void onBindViewHolder(Holder h,int pos){
            Channel c=shown.get(pos);h.t.setText(c.name+"\n"+c.category);h.t.setOnClickListener(v->resolveAndPlay(c));
            h.t.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_CENTER&&e.getAction()==KeyEvent.ACTION_UP){resolveAndPlay(c);return true;}return false;});
        }
        @Override public int getItemCount(){return shown.size();}
    }
    class Holder extends RecyclerView.ViewHolder{TextView t;Holder(TextView v){super(v);t=v;}}
}
