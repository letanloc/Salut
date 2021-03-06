package com.peak.salut;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class BackgroundClientRegistrationJob implements AsyncJob.OnBackgroundJob{


    private Salut salutInstance;
    private InetSocketAddress hostDeviceAddress;
    private final int BUFFER_SIZE = 65536;


    public BackgroundClientRegistrationJob(Salut salutInstance, InetSocketAddress hostDeviceAddress)
    {
        this.hostDeviceAddress = hostDeviceAddress;
        this.salutInstance = salutInstance;
    }


    @Override
    public void doOnBackground() {
        Log.d(Salut.TAG, "\nAttempting to transfer registration data with the server...");
        Socket registrationSocket = new Socket();

        try
        {
            registrationSocket.connect(hostDeviceAddress);
            registrationSocket.setReceiveBufferSize(BUFFER_SIZE);
            registrationSocket.setSendBufferSize(BUFFER_SIZE);

            //If this code is reached, we've connected to the server and will transfer data.
            Log.d(Salut.TAG, salutInstance.thisDevice.deviceName + " is connected to the server, transferring registration data...");

            //TODO Use buffered streams.
            Log.v(Salut.TAG, "Sending client registration data to server...");
            String serializedClient = LoganSquare.serialize(salutInstance.thisDevice);
            BufferedOutputStream bufferedOut = new BufferedOutputStream(registrationSocket.getOutputStream());
            DataOutputStream toClient = new DataOutputStream(bufferedOut);
            toClient.writeUTF(serializedClient);
            toClient.flush();

            Log.v(Salut.TAG, "Receiving server registration data...");
            BufferedInputStream bufferedInput = new BufferedInputStream(registrationSocket.getInputStream());
            DataInputStream fromServer = new DataInputStream(bufferedInput);

            if(!salutInstance.thisDevice.isRegistered)
            {
                String serializedServer = fromServer.readUTF();
                SalutDevice serverDevice = LoganSquare.parse(serializedServer, SalutDevice.class);
                serverDevice.serviceAddress = registrationSocket.getInetAddress().toString().replace("/", "");
                salutInstance.registeredHost = serverDevice;

                Log.d(Salut.TAG, "Registered Host | " + salutInstance.registeredHost.deviceName);

                salutInstance.thisDevice.isRegistered = true;
                salutInstance.dataReceiver.currentContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salutInstance.onRegistered != null)
                            salutInstance.onRegistered.call();
                    }
                });

                salutInstance.startListeningForData();
            }
            else {

                String registrationCode = fromServer.readUTF(); //TODO Use to verify

                salutInstance.thisDevice.isRegistered = false;
                salutInstance.registeredHost = null;
                salutInstance.cleanUpDataConnection(false);
                salutInstance.cleanUpDeviceConnection(false);
                salutInstance.disconnectFromDevice();

                Log.d(Salut.TAG, "This device has successfully been unregistered from the server.");

            }

            toClient.close();
            fromServer.close();

        }
        catch (IOException ex)
        {
            Log.e(Salut.TAG, "An error occurred while attempting to register or unregister.");
            salutInstance.dataReceiver.currentContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (salutInstance.onRegistrationFail != null)
                        salutInstance.onRegistrationFail.call();
                }
            });

            if(salutInstance.thisDevice.isRegistered && salutInstance.isConnectedToAnotherDevice)
            {
                //Failed to unregister so an outright disconnect is necessary.
                salutInstance.disconnectFromDevice();
            }
        }
        finally {
            try
            {
                registrationSocket.close();
            }
            catch(Exception ex)
            {
                Log.e(Salut.TAG, "Failed to close registration socket.");
            }
        }
    }
}
