package security.management;


import org.jivesoftware.smack.util.Base64;
import org.whispersystems.libaxolotl.*;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.impl.InMemoryAxolotlStore;

import security.trust.IIdentityWitness;
import security.trust.ITrustStore;
import security.trust.IWitnessGenerator;
import security.utils.KeyExchangeUtil;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.HashMap;


/**
 * Created by ben on 28/11/15.
 */
public class SecureParty
{
    private String email;
    private int numericId;
    private ECKeyPair signedPair;
    private byte[] signedPreKeySignature;
    private AxolotlStore axolotlStore;
    private ITrustStore trustStore;
    private IWitnessGenerator witnessGenerator;

    private HashMap<String, SecureSessionContext> sessions;

    public SecureParty(String email, ITrustStore trustStore, IWitnessGenerator witnessGenerator) throws
            CertificateException, NoSuchAlgorithmException, KeyStoreException,
            UnrecoverableEntryException, InvalidKeyException, IOException {

        this.trustStore = trustStore;
        this.witnessGenerator = witnessGenerator;
        sessions = new HashMap<>();
        this.email = email;
        this.numericId = email.hashCode();

        initializeAxolotlStore();
    }

    /**
     * Creates an IIdentityWitness using the generator supplied at creation.
     *
     * @return
     */
    public IIdentityWitness generateWitness()
    {
        return witnessGenerator.generateWitness(axolotlStore.getIdentityKeyPair().getPublicKey());
    }

    /**
     * Generates an in-memory axolotl store using the trust store
     * @return
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     * @throws UnrecoverableEntryException
     * @throws InvalidKeyException
     */
    private AxolotlStore generateKeyStore() throws CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException, UnrecoverableEntryException, InvalidKeyException {

        IdentityKeyPair idPair = trustStore.getIdentity();

        if(null == idPair)
        {
            //Set the identity key
            ECKeyPair ecPair = Curve.generateKeyPair();
            IdentityKey idKey = new IdentityKey(ecPair.getPublicKey());
            idPair = new IdentityKeyPair(idKey, ecPair.getPrivateKey());
            trustStore.setIdentity(idPair);
        }

        //Create an in-memory Axolotl axolotlStore (non-persistent)
        return new InMemoryAxolotlStore(idPair, numericId);
    }

    /**
     * Removes the peer entry from the trust store.
     * call this when peer notified you he lost his private key.
     *
     * IMPORTANT - This method does not end the current session if one exists!
     * call endSessionWithPeer for that!
     * @param peer
     */
    public void revokeTrustedIdentity(String peer) throws KeyStoreException {
        trustStore.RevokeTrustedIdentity(peer);
    }

    /**
     * Creates a unique numeric prekey id for compatibility with the axolotl library
     * @return
     */
    private int getSignedPrekeyId()
    {
        return (email + "signed").hashCode();
    }

    /**
     * Generates a key store, signed pair and inserts the signed pair to the store
     * @throws CertificateException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws UnrecoverableEntryException
     * @throws IOException
     */
    private void initializeAxolotlStore() throws CertificateException, InvalidKeyException,
            NoSuchAlgorithmException, KeyStoreException, UnrecoverableEntryException, IOException {

        axolotlStore = generateKeyStore();

        //generate a signed prekey pair
        signedPair = Curve.generateKeyPair();

        //Sign the signed prekey
        signedPreKeySignature = Curve.calculateSignature(axolotlStore.getIdentityKeyPair().getPrivateKey(),
                signedPair.getPublicKey().serialize());

        //Store the signed prekey
        SignedPreKeyRecord record = new SignedPreKeyRecord(getSignedPrekeyId(),
                0, signedPair, signedPreKeySignature);
        axolotlStore.storeSignedPreKey(getSignedPrekeyId(), record);

    }

    /**
     * Creates a key exchange message for peer
     * @param peer the peer identifier
     * @return
     */
    public String createKeyExchangeMessage(String peer)
    {
        //Create new ephemeral key
        ECKeyPair ephemeralPair = Curve.generateKeyPair();

        //Get an id for the prekey for this peer
        int prekeyId = peer.hashCode();
        PreKeyRecord record = new PreKeyRecord(prekeyId, ephemeralPair);

        // remove the old prekey in case we already had a conversation
        axolotlStore.removePreKey(prekeyId);

        //Store the new prekey
        axolotlStore.storePreKey(prekeyId, record);

        return KeyExchangeUtil.serialize(new PreKeyBundle(numericId, numericId, prekeyId,
                ephemeralPair.getPublicKey(), getSignedPrekeyId(), signedPair.getPublicKey(),
                signedPreKeySignature, axolotlStore.getIdentityKeyPair().getPublicKey()));
    }

    /**
     * Ends the current session peer.
     * @param peer
     */
    public void endSessionWithPeer(String peer)
    {
        if(isSessionInitialized(peer))
        {
            sessions.remove(peer);
        }
    }

    /**
     * @param peer the peer in question
     * @return true if this SecureParty already has an initialized conversation with peer
     */
    public boolean isSessionInitialized(String peer)
    {   return sessions.containsKey(peer);  }

    /**
     * Builds a secure session with peer, based on a key
     * exchange message generated by peer
     * @param peer
     * @param keyExchangeMessage
     * @return true is the user is trusted, false otherwise
     */
    public boolean consumeKeyExchangeMessage(String peer, String keyExchangeMessage) throws UnrecoverableEntryException,
            NoSuchAlgorithmException, KeyStoreException, UntrustedIdentityException, InvalidKeyException {

        //Create a session builder
        AxolotlAddress remoteAddress = new AxolotlAddress(peer, peer.hashCode());
        SessionBuilder builder = new SessionBuilder(axolotlStore, remoteAddress);

        //Process the counterpart prekey
        PreKeyBundle bundle = KeyExchangeUtil.deserialize(keyExchangeMessage);
        builder.process(bundle);
        SecureSessionContext ctx = new SecureSessionContext(new SessionCipher(axolotlStore, remoteAddress),
                bundle.getIdentityKey());

        if(isSessionInitialized(peer))
        {
            //Remove old session
            sessions.remove(peer);
        }

        sessions.put(peer,ctx);


        //Check if the peer is trusted
        return trustStore.isTrusted(peer,bundle.getIdentityKey().getPublicKey());
    }

    /**
     * Once the user receives a witness for the peers public key,
     * that is, some piece of data that can authenticate the identity key,
     * call this method.
     *
     * IMPORTANT - this method must be called when a session is in progress
     * with this peer, and the user is aware of the process
     *
     * @param peer
     * @param witness
     * @return true if the witness fits the session identity key
     */
    public boolean consumeIdentityWitness(String peer, IIdentityWitness witness) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        SecureSessionContext ctx = sessions.get(peer);
        if(null == ctx || !witness.authenticate(ctx.getSessionIdentityKey()))
        {
            return false;
        }
        trustStore.setTrustedIdentity(peer, ctx.getSessionIdentityKey());
        return true;
    }


    public String encrypt(String peer, String plaintext)
    {
        return Base64.encodeBytes(sessions.get(peer).getSessionCipher().encrypt(plaintext.getBytes()).serialize());
    }

    public byte[] decryptPreKeyMessage(String peer, String ciphertext) throws InvalidVersionException,
            InvalidMessageException, InvalidKeyException, DuplicateMessageException, InvalidKeyIdException,
            UntrustedIdentityException, LegacyMessageException {

        return sessions.get(peer).getSessionCipher()
                .decrypt(new PreKeyWhisperMessage(Base64.decode(ciphertext)));

    }


    /**
     * First tries to decrypt the given ciphertext as a WhisperMessage.
     * If failes, tries to decrypt it as a PreKeyWhisperMessage
     * @param peer
     * @param ciphertext
     * @return
     * @throws UntrustedIdentityException
     * @throws LegacyMessageException
     * @throws InvalidVersionException
     * @throws InvalidMessageException
     * @throws DuplicateMessageException
     * @throws InvalidKeyException
     * @throws InvalidKeyIdException
     * @throws NoSessionException
     */
    public String decrypt(String peer, String ciphertext) throws UntrustedIdentityException, LegacyMessageException,
            InvalidVersionException, InvalidMessageException, DuplicateMessageException,
            InvalidKeyException, InvalidKeyIdException, NoSessionException {

        byte[] plaintext;

        try {

            //Try to parse it as WhisperMessage
            plaintext = sessions.get(peer).getSessionCipher()
                    .decrypt(new WhisperMessage(Base64.decode(ciphertext)));

        } catch (InvalidMessageException e) {
            //We failed to parse it as WhisperMessage, maybe its PreKeyWhisperMessage
            plaintext = decryptPreKeyMessage(peer, ciphertext);

        }

        return new String(plaintext);
    }

    public String getOwner() {
        return email;
    }
}
