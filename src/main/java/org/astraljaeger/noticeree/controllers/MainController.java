package org.astraljaeger.noticeree.controllers;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.credentialmanager.storage.TemporaryStorageBackend;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.google.common.collect.Lists;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.astraljaeger.noticeree.DataTools.ConfigStore;
import org.astraljaeger.noticeree.DataTools.Data.Chatter;
import org.astraljaeger.noticeree.DataTools.Data.MixerHelper;
import org.astraljaeger.noticeree.DataTools.DataStore;
import org.astraljaeger.noticeree.Utils;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.awt.Desktop.*;

public class MainController {

    private final Logger logger = Logger.getLogger(MainController.class.getSimpleName());

    @FXML
    public TabPane mainTp;

    @FXML
    public TableView<Chatter> chattersTv;

    @FXML
    public TableColumn<String, Chatter> chattersNameCol;

    @FXML
    public TableColumn<String, Chatter> chattersMessageCol;

    @FXML
    public TableColumn<String, Chatter> chattersSoundsCol;

    @FXML
    public Button addBtn;

    @FXML
    public Button removeBtn;

    @FXML
    public Button editBtn;

    @FXML
    public Button playBtn;

    @FXML
    public Button stopBtn;

    @FXML
    public Button testAudioBtn;

    @FXML
    public Hyperlink usernameLink;

    @FXML
    public Hyperlink channelLink;

    @FXML
    public ChoiceBox<MixerHelper> audioOutputCb;

    @FXML
    public TextField channelTf;

    Stage primaryStage;
    DataStore store;
    TwitchClient client;

    DataStore dataStore;

    MixerHelper device;

    ObservableList<Chatter> chatterList;

    private static final String CLIENT_ID = "i76h7g9dys23tnsp4q5qbc9vezpwfb";

    public MainController(){
        chatterList = FXCollections.observableArrayList();
        Chatter chatter = new Chatter(0, "Test Chatter");
        chatter.setWelcomeMessage("The master has arrived");
        chatter.setSounds(Arrays.asList("Sound_0.mp3", "Sound_1.mp3", "Sound_2.mp3"));
        chatterList.add(chatter);
    }

    @FXML
    public void initialize(){

        logger.fine("Starting login process");
        client = doLogin();

        logger.fine("Setting up UI, restoring old config");
        setUiFromConfig();

        logger.fine("Inizialising database manager");
        dataStore = DataStore.getInstance();

        logger.fine("Binding data");
        setupTableView();

        logger.fine("Setup program exit event");
        if(primaryStage != null){
            primaryStage.onCloseRequestProperty().addListener(((observable, oldValue, newValue) -> {
                // TODO: Close db and things
                logger.info("Terminating application");
                client.getChat().disconnect();
                store.close();
                Platform.exit();
            }));
        }

        logger.fine("Setup tab change event");
        mainTp.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
            logger.info(String.format("Changed tab from '%d' to '%d'",
                    mainTp.getTabs().indexOf(oldValue),
                    mainTp.getTabs().indexOf(newValue)))
        );

        logger.fine("Setup add sound event");
        addBtn.setOnAction(event -> {
            // TODO: implement add option
        });

        logger.fine("Setup edit sound event");
        editBtn.setOnAction(event -> {
            // TODO: implement edit option
        });

        logger.fine("Setup remove sound event");
        removeBtn.setOnAction(event -> {
            // TODO: implement remove option
        });

    }

    private TwitchClient doLogin() {
        CredentialManager credentialManager = CredentialManagerBuilder.builder()
                .withStorageBackend(new TemporaryStorageBackend())
                .build();

        TwitchIdentityProvider twitchIdentityProvider = new TwitchIdentityProvider(CLIENT_ID, "", "");
        credentialManager.registerIdentityProvider(twitchIdentityProvider);
        String provider = twitchIdentityProvider.getProviderName();
        ConfigStore configStore = ConfigStore.getInstance();

        OAuth2Credential credential = null;
        OAuth2Credential result = null;
        String errorMessage = "";

        if(!configStore.getToken().equals("")){
            // get token from store
            String token = configStore.getToken();
            credential = new OAuth2Credential(provider, token);
            Optional<OAuth2Credential> storeOptional = twitchIdentityProvider.getAdditionalCredentialInformation(credential);
            if(storeOptional.isPresent()){
                result = storeOptional.get();
            }else {
                errorMessage = "Stored token is invalid, please re-enter";
            }
        }

        while(result == null){
            Dialog<Pair<String, Boolean>> dialog = Utils.createLoginDialog(errorMessage);
            Optional<Pair<String, Boolean>> pairOptional = dialog.showAndWait();
            String token = "";
            boolean save = false;

            if(pairOptional.isPresent()){
                Pair<String, Boolean> pair = pairOptional.get();
                token = pair.getKey();
                save = pair.getValue();
            }

            credential = new OAuth2Credential(provider, token);
            Optional<OAuth2Credential> loginOptional = twitchIdentityProvider.getAdditionalCredentialInformation(credential);
            if(loginOptional.isPresent()){
                result = loginOptional.get();
                if(save){
                    configStore.setToken(token);
                }
            }else {
                errorMessage = "Please enter a valid token";
            }
        }

       logger.info("Welcome " + result.getUserName());
        configStore.setUsername(result.getUserName());

        return TwitchClientBuilder.builder()
                .withCredentialManager(credentialManager)
                .withChatAccount(credential)
                .withEnableChat(true)
                .withEnableHelix(true)
                .withEnablePubSub(true)
                .build();
    }

    public void setUiFromConfig(){
        ConfigStore configStore = ConfigStore.getInstance();

        // Set links in about
        usernameLink.setText(configStore.getUsername());
        usernameLink.setOnAction(event -> {
            openUriInBrowser("https://www.twitch.tv/" + usernameLink.getText());
        });

        channelLink.setText(configStore.getChannel());
        channelLink.setOnAction(event -> {
            openUriInBrowser("https://www.twitch.tv/" + channelLink.getText());
        });

        channelTf.setText(configStore.getChannel());
        channelTf.textProperty().addListener(((observable, oldValue, newValue) -> {
            logger.fine("Setting channel name to " + newValue);
            channelTf.setText(newValue);
            configStore.setChannel(newValue);
        }));

        // Get list of audio devices and set configured device
        List<Mixer> devices = getPlaybackDevices();
        if(devices.size() != 0) {

            // Collect devices
            audioOutputCb.setItems(FXCollections.observableArrayList(
                    devices.stream()
                            .map(mixer -> new MixerHelper(
                                    mixer.getMixerInfo().getName(),
                                    mixer.getMixerInfo().getDescription(),
                                    mixer))
                            .collect(Collectors.toList()))
            );

            // Device changed event
            audioOutputCb.getSelectionModel().selectedItemProperty().addListener(((observable, oldValue, newValue) -> {
                device = newValue;
                logger.info("Setting new playback device");
                playTestSound(device);
                configStore.setDefaultOutputDevice(device.getMixer().getMixerInfo());
            }));

            // Restore playback device config
            Mixer.Info configuredMixer = configStore.getDefaultOutputDevice();
            if(configuredMixer != null) {
                int index = findMixerInfo(audioOutputCb.getItems(), configuredMixer);
                if (index != -1) {
                    logger.info(String.format("Found stored playback device: [%d] %s", index, configuredMixer));
                    audioOutputCb.getSelectionModel().select(index);
                }
            }
            else {
                // TODO: Consider giving a window to tell the user to configure a playback device
                logger.info("No playback device set, defaulting to 1st found device: " + ((Mixer)audioOutputCb.getItems().get(0)).getMixerInfo().getName());
                audioOutputCb.getSelectionModel().select(0);
            }
        }
        else {
            // TODO: flag error that no output devices were found
            logger.info("No playback devices were found!");
        }
    }

    private List<Mixer> getPlaybackDevices(){
        logger.info("Gathering audio playback devices");
        Line.Info playbackLine = new Line.Info(SourceDataLine.class);
        List<Mixer> results;
        List<Mixer.Info> infos = Lists.newArrayList(AudioSystem.getMixerInfo());
        results = infos.stream()
                .map(AudioSystem::getMixer)
                .filter(mixer -> mixer.isLineSupported(playbackLine))
                .collect(Collectors.toList());
        String devices = results.stream()
                .map(Mixer::getMixerInfo)
                .map(info -> "\t - " + info.getName() + " <> " + info.getDescription())
                .collect(Collectors.joining("\n"));
        logger.info("Found devices [" + results.size() + "]: \n" + devices);
        return results;
    }

    private void setupTableView(){
        logger.info("Setting up data bindings");
        chattersTv.setPlaceholder(new Label("Much empty! Such wow!"));
        chattersTv.setItems(chatterList);
        chattersNameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        chattersMessageCol.setCellValueFactory(new PropertyValueFactory<>("welcomeMessage"));
        chattersSoundsCol.setCellValueFactory(new PropertyValueFactory<>("sounds"));

        // Set better renderer for sounds
    }
    
    private void playTestSound(MixerHelper mixerInfo){

        // TODO: play sound on selected mixer
    }

    private int findMixerInfo(List<MixerHelper> helpers, Mixer.Info target){
        int i = 0;
        for(MixerHelper helper: helpers){
            Mixer.Info info = helper.getMixer().getMixerInfo();
            if(info.getName().equals(target.getName()) && info.getDescription().equals(target.getDescription())){
                return i;
            }
            i++;
        }
        return -1;
    }

    public void setPrimaryStage(Stage primaryStage){
        this.primaryStage = primaryStage;
    }

    private void openUriInBrowser(String uri){
        if (isDesktopSupported()) {
            var desktop = getDesktop();
            if(desktop.isSupported(Action.BROWSE)){
                try {
                    desktop.browse(new URI(uri));
                }catch (Exception ignored){}
            }
        }
    }
}
