package nl.tudelft.cs4160.trustchain_android.ZeroKnowledge;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Created by Shiv on 12/29/2017.
 */

public class ZkpHashChain {
    public String getRandomProof() {
        return randomProof;
    }

    public String getSignedDigest() {
        return signedDigest;
    }

    private String randomProof;
    private String signedDigest;

    MessageDigest md = null;

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

        if(md == null)
        {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        for(int i=0;i<26;i++)
        {
            randomProofBytes = md.digest(randomProofBytes);
        }

        // TODO: sign the digest here before assignment.
        signedDigest = randomProofBytes.toString();

        return;
    }


    public String genVerificationProof(String randomGen, int actvalValue, int toProve)
    {
        byte[] proverdigest = randomGen.getBytes();

        if(1+actvalValue-toProve < 0)
        {
            // This case cannot have a proof.
            return null;
        }
        if(md == null)
        {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        for(int i=0;i<(1+actvalValue-toProve);i++)
        {
            proverdigest = md.digest(proverdigest);
        }

        return proverdigest.toString();
    }


    public boolean verifyZkpProof(String proofHash, String hashInBlock, int toProve)
    {
        byte[] verifierDigest = null;
        //TODO: Check for the signature here...
        verifierDigest = proofHash.getBytes();

        for(int i=0;i<toProve;i++)
        {
            verifierDigest = md.digest(verifierDigest);
        }

        return (hashInBlock.equals(verifierDigest.toString()));
    }
}
