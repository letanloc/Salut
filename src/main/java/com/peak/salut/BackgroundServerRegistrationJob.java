package com.peak.salut;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class BackgroundServerRegistrationJob implements AsyncJob.OnBackgroundJob{

    private Salut salutInstance;
    private Socket clientSocket;

    public BackgroundServerRegistrationJob(Salut salutInstance, Socket clientSocket)
    {
        this.clientSocket = clientSocket;
        this.salutInstance = salutInstance;
    }

    @Override
    public void doOnBackground() {
        try {
            //If this code is reached, a client has connected and transferred data.
            Log.d(Salut.TAG, "A device has connected to the server, transferring data...");

            //TODO Use buffered streams.
            Log.v(Salut.TAG, "Receiving client registration data...");
            BufferedInputStream bufferInput = new BufferedInputStream(clientSocket.getInputStream());
            DataInputStream fromClient = new DataInputStream(bufferInput);
            String serializedClient = fromClient.readUTF();

            SalutDevice clientDevice = LoganSquare.parse(serializedClient, SalutDevice.class);
            clientDevice.serviceAddress = clientSocket.getInetAddress().toString().replace("/", "");

            BufferedOutputStream bufferedOut = new BufferedOutputStream(clientSocket.getOutputStream());
            DataOutputStream toClient = new DataOutputStream(bufferedOut);

            if (!clientDevice.isRegistered) {

                Log.v(Salut.TAG, "Sending server registration data...");
                String serializedServer = LoganSquare.serialize(salutInstance.thisDevice);
                toClient.writeUTF(serializedServer);
                toClient.flush();

                Log.d(Salut.TAG, "Registered device and user: " + clientDevice);
                clientDevice.isRegistered = true;
                final SalutDevice finalDevice = clientDevice; //Allows us to get around having to add the final modifier earlier.
                if (salutInstance.registeredClients.isEmpty()) {
                    salutInstance.startListeningForData();
                }
                salutInstance.registeredClients.add(clientDevice);

                if (salutInstance.onDeviceRegisteredWithHost != null) {
                    salutInstance.dataReceiver.currentContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            salutInstance.onDeviceRegisteredWithHost.call(finalDevice);
                        }
                    });
                }

            } else {
                Log.d(Salut.TAG, "\nReceived request to unregister device.\n");

                Log.v(Salut.TAG, "Sending registration code...");
                toClient.writeUTF(Salut.UNREGISTER_CODE);
                toClient.flush();

                for(SalutDevice registered : salutInstance.registeredClients)
                {
                    if(registered.serviceAddress.equals(clientSocket.getInetAddress().toString().replace("/", "")))
                    {
                        salutInstance.registeredClients.remove(registered);
                        Log.d(Salut.TAG, "\nSuccesfully unregistered device.\n");
                    }
                }
            }

            fromClient.close();
            toClient.close();

        } catch (Exception ex) {
            Log.e(Salut.TAG, "An error occurred while dealing with registration for a client.");
        }
        finally {
            try
            {
                clientSocket.close();
            }
            catch (Exception ex)
            {
                Log.e(Salut.TAG, "Failed to close registration socket.");
            }
        }
    }
}
