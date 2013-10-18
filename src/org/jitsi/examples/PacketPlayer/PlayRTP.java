package org.jitsi.examples.PacketPlayer;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.*;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jitsi.examples.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.event.VideoEvent;
import org.jitsi.util.event.VideoListener;
import org.jitsi.util.swing.VideoContainer;

class VideoFrame extends JFrame
{
    private static final long serialVersionUID = 1L;
    private VideoContainer vc;

    VideoFrame(Component video)
    {
        super("Video");
        vc = new VideoContainer(video, false);
        this.add(vc);
        this.setSize(1000, 1000);
    }
}

/**
 * Play RTP from a file.
 */
public class PlayRTP
{
    private static boolean started;

    private MediaStream mediaStream;

    boolean foundVideo;

    private synchronized void checkForVideo()
    {
        if (!foundVideo)
        {
            System.out.println("Still finding video");

            List<Component> videos = ((VideoMediaStream) mediaStream).getVisualComponents();
            if (! videos.isEmpty())
            {
                System.out.println("Found Video!");

                foundVideo = true;
                final Component video = videos.get(0);
                SwingUtilities.invokeLater(new Runnable(){

                    @Override
                    public void run() {
                        System.out.println("Displaying Video!");

                        VideoFrame videoFrame = new VideoFrame(video);
                        videoFrame.setVisible(true);
                    }
                });
            }
        }
    }

    private StreamConnector connector;
    /**
     * Initializes the receipt of audio.
     *
     * @return <tt>true</tt> if this instance has been successfully initialized
     * @throws Exception if anything goes wrong while initializing this instance
     */
    private boolean playMedia(String filename, String encoding,
        double clockRate, byte dynamicRTPPayloadType, int ssrc) throws Exception
    {
        /*
         * Prepare for the start of the transmission i.e. initialize the
         * MediaStream instances.
         */
        MediaService mediaService = LibJitsi.getMediaService();
        MediaFormat format =
            mediaService.getFormatFactory().createMediaFormat(encoding,
                clockRate);

        MediaDevice device =
            mediaService.getDefaultDevice(format.getMediaType(), MediaUseCase.CALL);
        mediaStream = mediaService.createMediaStream(device);
        mediaStream.setDirection(MediaDirection.RECVONLY);

        if (format.getMediaType().equals(MediaType.VIDEO))
        {
            foundVideo = false;

            ((VideoMediaStream) mediaStream).addVideoListener(new VideoListener(){

                @Override
                public void videoAdded(VideoEvent event)
                {
                    checkForVideo();
                }

                @Override
                public void videoRemoved(VideoEvent event)
                {
                    checkForVideo();
                }

                @Override
                public void videoUpdate(VideoEvent event)
                {
                    checkForVideo();
                }
            });

            PropertyChangeListener pchange = new PropertyChangeListener(){
                @Override
                public synchronized void propertyChange(PropertyChangeEvent evt)
                {
                    System.out.println("Change event: " + evt);
                    checkForVideo();
                }
            };

            mediaStream.addPropertyChangeListener(pchange);
            pchange.propertyChange(null);
        }


        /*
         * The MediaFormat instances which do not have a static RTP payload type
         * number association must be explicitly assigned a dynamic RTP payload
         * type number.
         */
        if (dynamicRTPPayloadType != -1)
        {
            mediaStream.addDynamicRTPPayloadType(dynamicRTPPayloadType, format);
        }

        mediaStream.setFormat(format);


        // connector
        connector = new PCapStreamConnector(filename, ssrc, dynamicRTPPayloadType);
        mediaStream.setConnector(connector);
        mediaStream.start();

        return true;
    }

    /**
     * Close the <tt>MediaStream</tt>s.
     */
    private void close()
    {
        if (mediaStream != null)
        {
            try
            {
                mediaStream.stop();
            }
            finally
            {
                mediaStream.close();
                mediaStream = null;
            }
        }
    }

    private static void initIfRequired()
    {
        if (!started)
            LibJitsi.start();
    }

    private static void shutdown()
    {
        LibJitsi.stop();
    }

    /*
     * Blocking
     */
    public void playFile(String filename, String encoding, double clockRate,
        byte dynamicRTPPayloadType, int ssrc)
    {
        close();
        initIfRequired();

        try
        {
            playMedia(filename, encoding, clockRate, dynamicRTPPayloadType, ssrc);
            while (connector.getDataSocket().isConnected())
            {
                Thread.sleep(100);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            close();
        }
    }

    public static void main(String[] args) throws Exception
    {
        // We need one parameter . For example,
        // ant run-example -Drun.example.name=PlayRTP
        // -Drun.example.arg.line="--filename=test.pcap"
        if (args.length < 1)
        {
            prUsage();
        }
        else
        {
            Map<String, String> argMap = AVTransmit2.parseCommandLineArgs(args);

            try
            {
                PlayRTP playRTP = new PlayRTP();
                playRTP.playFile(argMap.get(FILENAME), "SILK", 8000,
                    (byte) 96, -1);
            }
            finally
            {
                shutdown();
            }
            System.exit(0);
        }
    }

    /**
     * The filename to play
     */
    private static final String     FILENAME = "--filename=";

    /**
     * The list of command-line arguments accepted as valid.
     */
    private static final String[][] ARGS     = {{FILENAME,
            "The filename to play."          },};


    /**
     * Outputs human-readable description about the usage.
     */
    private static void prUsage()
    {
        PrintStream err = System.err;

        err.println("Usage: " + PlayRTP.class.getName() + " <args>");
        err.println("Valid args:");
        for (String[] arg : ARGS)
            err.println("  " + arg[0] + " " + arg[1]);
    }
}
