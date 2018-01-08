package nl.tudelft.cs4160.trustchain_android.ZeroKnowledge;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Created by Shiv on 12/29/2017.
 */

public class ZkpHashChain {
    public String getRandomProof() {
        return randomProof;
    }


    public byte[] getSignedDigest() {
        return signedDigest;
    }

    private String randomProof;
    private byte[] signedDigest;

    private static final String TAG = ZkpHashChain.class.getName();

    private MessageDigest md = null;

    public ZkpHashChain()
    {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
    }

    public void zkpAuthenticate(int toAuthenticate)
    {
        // Generate the random string, and hash it 'toAuthenticate' times.
        randomProof = UUID.randomUUID().toString();

        byte[] randomProofBytes = null;
        try {
            randomProofBytes = randomProof.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }


        for(int i=0;i<(1+toAuthenticate);i++)
        {
            randomProofBytes = md.digest(randomProofBytes);
        }
        
        signedDigest = randomProofBytes;

        Log.e(TAG,"ZkpHashChain random number generated is "+ randomProof);
        Log.e(TAG,"ZkpHashChain hashValue is " + Arrays.toString(signedDigest));

        return;
    }


    public byte[] genVerificationProof(String randomGen, int actualValue, int toProve)
    {
        byte[] proverdigest = randomGen.getBytes();

        if(1+actualValue-toProve < 0)
        {
            // This case cannot have a proof.
            return null;
        }

        for(int i=0;i<(1+actualValue-toProve);i++)
        {
            proverdigest = md.digest(proverdigest);
        }

        return proverdigest;
    }


    public boolean verifyZkpProof(byte[] proofHash, byte[] hashInBlock, int toProve)
    {
//        if(proofHash == null || toProve < 0)
//            return false;
        for(int i=0;i<toProve;i++)
        {
            proofHash = md.digest(proofHash);
        }

        return (Arrays.equals(proofHash,hashInBlock));
    }
}
