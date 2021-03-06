package org.jitsi.examples.PacketPlayer;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;
import java.util.prefs.*;

import javax.sdp.*;
import javax.swing.*;
import javax.swing.table.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.device.AudioSystem.DataFlow;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.Logger;

public class RTPPlayer
{
    private static final Logger logger = Logger.getLogger(RTPPlayer.class);

    private final static int PLAY = 6;

    private final class TableMouseListener
        implements MouseListener
    {
        @Override
        public void mouseClicked(MouseEvent event)
        {
            int row = mtable.rowAtPoint(event.getPoint());
            int col = mtable.columnAtPoint(event.getPoint());

            if (col == PLAY)
            {
                // This is the play column
                if (threads[row] == null)
                {
                    playRow(row);
                    myTable.fireTableCellUpdated(row, col);
                }
                else
                {
                    // Stop playing
                    System.out.println("Stop playing row");
                    threads[row].playRTP.stopPlaying();
                    threads[row] = null;
                    myTable.fireTableCellUpdated(row, col);
                }
            }
        }

        @Override
        public void mouseEntered(MouseEvent e){}

        @Override
        public void mouseExited(MouseEvent e){}

        @Override
        public void mousePressed(MouseEvent e){}

        @Override
        public void mouseReleased(MouseEvent e){}
    }

    private final JFrame mframe = new JFrame();
    private final JFileChooser fc = new JFileChooser();
    private JLabel lblFileName;
    private JTable mtable;
    private AbstractTableModel myTable;

    /**
     * Launch the application.
     */
    public static void main(String[] args)
    {
        System.setProperty("java.util.logging.config.file",
            "logging.properties");
        try
        {
            LogManager.getLogManager().readConfiguration();
        }
        catch (SecurityException e1)
        {
            e1.printStackTrace();
        }
        catch (IOException e1)
        {
            e1.printStackTrace();
        }

        final String possibleInputFile = args.length > 0 ? args[0] : "";

        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                try
                {
                    RTPPlayer window = new RTPPlayer();
                    window.mframe.setVisible(true);
                    if (new File(possibleInputFile).exists())
                    {
                        window.loadFile(new File(possibleInputFile));
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    private JComboBox<String> codecComboBox;
    private JComboBox<String> codecComboBox2;
    private JComboBox<String> audioDeviceComboBox;
    private JComboBox<Boolean> autoComboBox;
    private JTextField autoItersInput;
    private JComboBox<Boolean> stopOnNoAudioComboBox;

    private org.jitsi.examples.PacketPlayer.PlayRTPThread[] threads;

    public void playRow(final int row)
    {
        final StreamIdentifier stream = streams.get(row);
        List<Byte> payloadList = stream.getPacketTypes();
        byte ipt;
        int i = 0;
        do
        {
            ipt = payloadList.get(i);
            i ++;
        } while(ipt < 0);

        final byte initialPT = ipt;
        final List<Byte> dynamicPayloadTypes = new LinkedList<Byte>();
        final List<MediaFormat> dynamicFormats = new LinkedList<MediaFormat>();
        boolean first = true;
        LibJitsi.start();
        for (byte pt : payloadList)
        {
            if (pt > 34)
            {
                dynamicPayloadTypes.add(pt);

                // Get the codec we should use for dynamic payload types from
                // the drop down box.

                JComboBox<String> thisBox = first ? codecComboBox : codecComboBox2;
                first = false;
                String codec =
                    ((String) thisBox.getSelectedItem()).split("/")[0];
                double frequency = Double.parseDouble(
                    ((String) thisBox.getSelectedItem()).split("/")[1]);
                MediaFormat dynamicFormat = LibJitsi.getMediaService()
                    .getFormatFactory().createMediaFormat(codec, frequency);
                dynamicFormats.add(dynamicFormat);
            }
        }
        LibJitsi.stop();

        final org.jitsi.examples.PacketPlayer.PlayRTPThread myThread = new PlayRTPThread()
        {
            @Override
            public void run()
            {
                playRTP = new PlayRTP(); // Also initializes Libjitsi

                // Set the appropriate output device
                String selectedDeviceStr = (String)audioDeviceComboBox.getSelectedItem();
                CaptureDeviceInfo2 selectedDevice = null;
                AudioSystem audioSystem = ((MediaServiceImpl)LibJitsi.getMediaService()).getDeviceConfiguration().getAudioSystem();
                for (CaptureDeviceInfo2 device : audioSystem.getDevices(DataFlow.PLAYBACK)) {
                  if (device.getName().equals(selectedDeviceStr)) {
                    selectedDevice = device;
                    break;
                  }
                }
                System.out.println((selectedDevice == null) ? "Couldn't find output device." : "Selected device: " + selectedDevice.getName());
                audioSystem.setDevice(DataFlow.PLAYBACK, selectedDevice, true);

                // Set the initial format of this stream from the initial
                // payload type - it's either a standard payload type or the
                // same as the dynamic format we just calculated.
                MediaFormat initialFormat = null;
                if (initialPT < 0)
                {
                    // Ignore me.
                    System.err.print("Assert : PT shouldn't be < 0");
                }
                else if (initialPT <= 34)
                {
                    String codec = SdpConstants.avpTypeNames[initialPT];
                    initialFormat = LibJitsi.getMediaService()
                        .getFormatFactory().createMediaFormat(codec,
                            (double)8000); // g711 and 722 using 8K always
                }
                else
                {
                    initialFormat = dynamicFormats.get(0);
                }

                if ((Boolean)autoComboBox.getSelectedItem() == true)
                {
                  // Auto mode - loop round a bunch of times.
                  playRTP.playFileInAutoMode(lblFileName.getText(),
                      initialFormat, dynamicPayloadTypes, dynamicFormats, stream,
                      Integer.parseInt(autoItersInput.getText()),
                      (Boolean)stopOnNoAudioComboBox.getSelectedItem());
                }
                else
                {
                  // Regular mode - play once.
                  playRTP.playFile(lblFileName.getText(), initialFormat,
                                dynamicPayloadTypes, dynamicFormats, stream);
                }

                final PlayRTPThread player = this;

                SwingUtilities.invokeLater(new Runnable(){
                    public void run()
                    {
                        if (threads[row] == player)
                        {
                            threads[row] = null;
                            myTable.fireTableCellUpdated(row, PLAY);
                        }
                    }
                });
            }

        };

        myThread.start();
        threads[row] = myThread;
    }

  /**
     * Create the application.
     */
    public RTPPlayer()
    {
        initialize();
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initialize()
    {
        mframe.setBounds(100, 100, 800, 366);
        mframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JButton btnChooseFile = new JButton("Choose File");
        btnChooseFile.setDropTarget(new DropTarget() {
          public synchronized void drop(DropTargetDropEvent evt) {
            try {
              evt.acceptDrop(DnDConstants.ACTION_COPY);
              List<File> droppedFiles = (List<File>)
                  evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

              if (droppedFiles.size() == 1)
              {
                loadFile(droppedFiles.get(0));
              }

            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
        });
        btnChooseFile.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent arg0)
            {
                Preferences prefs = Preferences.userNodeForPackage(getClass());

                String lastLocation = prefs.get("location", "");
                System.out.println("lastLocation: " + lastLocation);
                if (!"".equals(lastLocation))
                {
                    File file = new File(lastLocation);
                    if (file.exists())
                    {
                        fc.setCurrentDirectory(file);
                    }
                }

                int returnVal = fc.showOpenDialog(mframe);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    File file = fc.getSelectedFile();
                    System.out.println("Got " + file);
                    if (file.exists())
                    {
                        fc.setCurrentDirectory(file);
                        prefs.put("location", file.getAbsolutePath());
                        try {
                            prefs.flush();
                        } catch (BackingStoreException e) {
                            e.printStackTrace();
                        }

                        loadFile(file);
                    }
                }
            }
        });
        SpringLayout springLayout = new SpringLayout();
        springLayout.putConstraint(SpringLayout.NORTH, btnChooseFile, 0,
            SpringLayout.NORTH, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.WEST, btnChooseFile, 0,
            SpringLayout.WEST, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.EAST, btnChooseFile, 137,
            SpringLayout.WEST, mframe.getContentPane());
        mframe.getContentPane().setLayout(springLayout);
        mframe.getContentPane().add(btnChooseFile);

        JLabel lblCurrentFile = new JLabel("Current File:");
        springLayout.putConstraint(SpringLayout.NORTH, lblCurrentFile, 2,
            SpringLayout.SOUTH, btnChooseFile);
        springLayout.putConstraint(SpringLayout.WEST, lblCurrentFile, 0,
            SpringLayout.WEST, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.EAST, lblCurrentFile, 113,
            SpringLayout.WEST, mframe.getContentPane());
        mframe.getContentPane().add(lblCurrentFile);

        lblFileName = new JLabel("");
        springLayout.putConstraint(SpringLayout.NORTH, lblFileName, 2,
            SpringLayout.SOUTH, lblCurrentFile);
        springLayout.putConstraint(SpringLayout.WEST, lblFileName, 10,
            SpringLayout.WEST, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.EAST, lblFileName, 600,
            SpringLayout.WEST, mframe.getContentPane());
        mframe.getContentPane().add(lblFileName);

        myTable = new AbstractTableModel()
        {
            String[] columnNames =
            { "SSRC", "SSRC", "SRC", "DST", "Packets", "PT", "Play" };

            public String getColumnName(int col)
            {
                return columnNames[col].toString();
            }

            public int getRowCount()
            {
                return streams.size();
            }

            public int getColumnCount()
            {
                return columnNames.length;
            }

            public Object getValueAt(int row, int col)
            {
                StreamIdentifier stream = streams.get(row);
                switch(col)
                {
                case 0:
                    return String.format("0x%08x",stream.getSSRC());
                case 1:
                    return stream.getSSRC();
                case 2:
                    return stream.getSource();
                case 3:
                    return stream.getDestination();
                case 4:
                    return stream.getPacketCount();
                case 5:
                    return stream.getPacketTypes();
                case 6:
                    if (threads[row] == null)
                    {
                        return "Click to Play";
                    }
                    else
                    {
                        return "Click to Stop";
                    }
                }

                return null;
            }

            public boolean isCellEditable(int row, int col)
            {
                return false;
            }

            public void setValueAt(Object value, int row, int col)
            {
            }
        };

        // Table is added to view after the other items so it can be located properly

        codecComboBox = new JComboBox<String>();
        codecComboBox.setModel(new DefaultComboBoxModel<String>(new String[]
        { "SILK/8000", "SILK/16000","H264/90000" }));
        springLayout.putConstraint(SpringLayout.NORTH, codecComboBox, 0,
            SpringLayout.NORTH, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.WEST, codecComboBox, -152,
            SpringLayout.EAST, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.EAST, codecComboBox, 0,
            SpringLayout.EAST, mframe.getContentPane());
        mframe.getContentPane().add(codecComboBox);

        JLabel lblAssumeCodecFor = new JLabel("Dynamic PT 1");
        springLayout.putConstraint(SpringLayout.NORTH, lblAssumeCodecFor, 0,
            SpringLayout.NORTH, btnChooseFile);
        springLayout.putConstraint(SpringLayout.EAST, lblAssumeCodecFor, -10,
            SpringLayout.WEST, codecComboBox);
        mframe.getContentPane().add(lblAssumeCodecFor);

        codecComboBox2 = new JComboBox<String>();
        codecComboBox2.setModel(new DefaultComboBoxModel<String>(new String[]
        { "SILK/8000", "SILK/16000","H264/90000" }));
        springLayout.putConstraint(SpringLayout.NORTH, codecComboBox2, 0,
            SpringLayout.SOUTH, codecComboBox);
        springLayout.putConstraint(SpringLayout.WEST, codecComboBox2, -152,
            SpringLayout.EAST, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.EAST, codecComboBox2, 0,
            SpringLayout.EAST, mframe.getContentPane());
        mframe.getContentPane().add(codecComboBox2);

        JLabel lblAssumeCodecFor2 = new JLabel("Dynamic PT 2");
        springLayout.putConstraint(SpringLayout.NORTH, lblAssumeCodecFor2, 24,
            SpringLayout.NORTH, btnChooseFile);
        springLayout.putConstraint(SpringLayout.EAST, lblAssumeCodecFor2, -10,
            SpringLayout.WEST, codecComboBox);
        mframe.getContentPane().add(lblAssumeCodecFor2);

        // Choose the output audio device
        LibJitsi.start();
        MediaService mediaService = LibJitsi.getMediaService();
        final AudioSystem audioSystem = ((MediaServiceImpl)mediaService).getDeviceConfiguration().getAudioSystem();
        LinkedHashMap<String, String> deviceListMap = audioSystem.getAllDevices(DataFlow.PLAYBACK);
        List<String> devices = new ArrayList<String>();
        for (String device: deviceListMap.keySet())
        {
            devices.add(device);
            System.out.println("Found device: " + device);
        }
        LibJitsi.stop();

        audioDeviceComboBox = new JComboBox<>();
        audioDeviceComboBox.setModel(new DefaultComboBoxModel<>(devices.toArray(new String[0])));
        springLayout.putConstraint(SpringLayout.NORTH, audioDeviceComboBox, 0,
                SpringLayout.SOUTH, codecComboBox2);
        springLayout.putConstraint(SpringLayout.EAST, audioDeviceComboBox, 0,
                SpringLayout.EAST, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.WEST, audioDeviceComboBox, -152,
            SpringLayout.EAST, audioDeviceComboBox);
        mframe.getContentPane().add(audioDeviceComboBox);

        JLabel lblAudioDevice = new JLabel("Audio device:");
        springLayout.putConstraint(SpringLayout.NORTH, lblAudioDevice, 0,
            SpringLayout.NORTH, audioDeviceComboBox);
        springLayout.putConstraint(SpringLayout.EAST, lblAudioDevice, -10,
            SpringLayout.WEST, audioDeviceComboBox);
        mframe.getContentPane().add(lblAudioDevice);

        // Automation settings. Is auto-mode on? How many iterations? Stop on no audio?
        JLabel lblAutoHeading = new JLabel("Parameters for automatic mode:");
        springLayout.putConstraint(SpringLayout.NORTH, lblAutoHeading, 6,
            SpringLayout.SOUTH, lblAudioDevice);
        springLayout.putConstraint(SpringLayout.WEST, lblAutoHeading, 2,
            SpringLayout.WEST, mframe.getContentPane());
        mframe.getContentPane().add(lblAutoHeading);

        // 1) Is automation mode on? lblAuto, autoComboBox (true/false)
        JLabel lblAuto = new JLabel("Run in auto-mode?");
        springLayout.putConstraint(SpringLayout.NORTH, lblAuto, 6,
            SpringLayout.SOUTH, lblAutoHeading);
        springLayout.putConstraint(SpringLayout.WEST, lblAuto, 2,
            SpringLayout.WEST, mframe.getContentPane());
        mframe.getContentPane().add(lblAuto);

        autoComboBox = new JComboBox<>();
        autoComboBox.setModel(new DefaultComboBoxModel<>(new Boolean[]
                                                             {false,true}));
        springLayout.putConstraint(SpringLayout.NORTH, autoComboBox, 0,
                SpringLayout.NORTH, lblAuto);
        springLayout.putConstraint(SpringLayout.WEST, autoComboBox, 2,
                SpringLayout.EAST, lblAuto);
        springLayout.putConstraint(SpringLayout.EAST, autoComboBox, 55,
            SpringLayout.WEST, autoComboBox);
        mframe.getContentPane().add(autoComboBox);

        // 2) How many iterations should we run? lblAutoIters[2], autoItersInput
        JLabel lblIters = new JLabel("N iterations");
        springLayout.putConstraint(SpringLayout.NORTH, lblIters, 0,
                SpringLayout.NORTH, lblAuto);
        springLayout.putConstraint(SpringLayout.WEST, lblIters, 10,
                SpringLayout.EAST, autoComboBox);
        mframe.getContentPane().add(lblIters);
        JLabel lblIters2 = new JLabel("(0=infinite)");
        springLayout.putConstraint(SpringLayout.NORTH, lblIters2, 2,
                SpringLayout.SOUTH, lblIters);
            springLayout.putConstraint(SpringLayout.WEST, lblIters2, 2,
                SpringLayout.WEST, lblIters);
            mframe.getContentPane().add(lblIters2);

        autoItersInput = new JTextField();
        autoItersInput.setText("1");
        springLayout.putConstraint(SpringLayout.NORTH, autoItersInput, 0,
                SpringLayout.NORTH, lblIters);
        springLayout.putConstraint(SpringLayout.WEST, autoItersInput, 2,
                SpringLayout.EAST, lblIters);
        springLayout.putConstraint(SpringLayout.EAST, autoItersInput, 60,
                SpringLayout.WEST, autoItersInput);
        mframe.getContentPane().add(autoItersInput);

        // 3) Stop if an iteration has no audio? lblAutoStopOnNoAudio[2], stopOnNoAudioComboBox
        JLabel lblAutoStopOnNoAudio = new JLabel("Stop if iter");
        springLayout.putConstraint(SpringLayout.NORTH, lblAutoStopOnNoAudio, 0,
                SpringLayout.NORTH, lblAuto);
        springLayout.putConstraint(SpringLayout.WEST, lblAutoStopOnNoAudio, 10,
                SpringLayout.EAST, autoItersInput);
        mframe.getContentPane().add(lblAutoStopOnNoAudio);
        JLabel lblAutoStopOnNoAudio2 = new JLabel("has no audio");
        springLayout.putConstraint(SpringLayout.NORTH, lblAutoStopOnNoAudio2, 2,
                SpringLayout.SOUTH, lblAutoStopOnNoAudio);
        springLayout.putConstraint(SpringLayout.WEST, lblAutoStopOnNoAudio2, 2,
                SpringLayout.WEST, lblAutoStopOnNoAudio);
        mframe.getContentPane().add(lblAutoStopOnNoAudio2);

        stopOnNoAudioComboBox = new JComboBox<>();
        stopOnNoAudioComboBox.setModel(new DefaultComboBoxModel<>(new Boolean[]
                                                             {true,false}));
        springLayout.putConstraint(SpringLayout.NORTH, stopOnNoAudioComboBox, 0,
                SpringLayout.NORTH, lblAuto);
        springLayout.putConstraint(SpringLayout.WEST, stopOnNoAudioComboBox, 2,
                SpringLayout.EAST, lblAutoStopOnNoAudio2);
        springLayout.putConstraint(SpringLayout.EAST, stopOnNoAudioComboBox, 55,
            SpringLayout.WEST, stopOnNoAudioComboBox);
        mframe.getContentPane().add(stopOnNoAudioComboBox);

        // Now add the table of streams.
        JScrollPane scrollPane_1 = new JScrollPane();
        springLayout.putConstraint(SpringLayout.NORTH, scrollPane_1, 10,
            SpringLayout.SOUTH, autoComboBox);
        springLayout.putConstraint(SpringLayout.WEST, scrollPane_1, 0,
            SpringLayout.WEST, btnChooseFile);
        springLayout.putConstraint(SpringLayout.SOUTH, scrollPane_1, 0,
            SpringLayout.SOUTH, mframe.getContentPane());
        springLayout.putConstraint(SpringLayout.EAST, scrollPane_1, 0,
            SpringLayout.EAST, mframe.getContentPane());
        mframe.getContentPane().add(scrollPane_1);

        mtable = new JTable(myTable);
        mtable.addMouseListener(new TableMouseListener());
        scrollPane_1.setViewportView(mtable);
        mtable.setRowSelectionAllowed(false);
    }

    List<StreamIdentifier> streams = new ArrayList<StreamIdentifier>();

    protected void loadFile(File file)
    {
        clearUpLastRun();

        lblFileName.setText(file.toString());

        streams = StreamIdentifier.fromFile(file.toString());

        for (StreamIdentifier stream : streams)
        {
            System.out.println(String.format("0x%08x = %s packets\n PTs: %s",
                stream.getSSRC(), stream.getPacketCount(), stream.getPacketTypes()));
        }
        myTable.fireTableDataChanged();

        threads = new org.jitsi.examples.PacketPlayer.PlayRTPThread[streams.size()];
    }

    private void clearUpLastRun()
    {
        if (threads != null)
        {
            for (org.jitsi.examples.PacketPlayer.PlayRTPThread thread : threads)
            {
                if (thread != null)
                {
                    thread.playRTP.stopPlaying();
                }
            }

            threads = null;
        }
    }
}
