package vn.fs.dto.chat;

public class ChatStatusDto {

	private final String provider;
	private final String model;
	private final boolean aiEnabled;
    private String health="unknown";
    public String getHealth(){return health;}
    public ChatStatusDto health(String value){health=value;return this;}

	public ChatStatusDto(String provider, String model, boolean aiEnabled) {
		this.provider = provider;
		this.model = model;
		this.aiEnabled = aiEnabled;
	}

	public String getProvider() { return provider; }
	public String getModel() { return model; }
	public boolean isAiEnabled() { return aiEnabled; }
	public String getArchitecture() { return aiEnabled ? "structured-v1" : "dialogue-v3-local"; }
}
