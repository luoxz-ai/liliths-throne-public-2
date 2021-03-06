package com.lilithsthrone.game.character;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.lilithsthrone.game.Game;
import com.lilithsthrone.game.character.attributes.Attribute;
import com.lilithsthrone.game.character.body.valueEnums.BodySize;
import com.lilithsthrone.game.character.body.valueEnums.CupSize;
import com.lilithsthrone.game.character.body.valueEnums.Muscle;
import com.lilithsthrone.game.character.effects.StatusEffect;
import com.lilithsthrone.game.character.fetishes.Fetish;
import com.lilithsthrone.game.character.gender.Gender;
import com.lilithsthrone.game.character.npc.NPC;
import com.lilithsthrone.game.character.npc.misc.NPCOffspring;
import com.lilithsthrone.game.character.persona.NameTriplet;
import com.lilithsthrone.game.character.persona.PersonalityTrait;
import com.lilithsthrone.game.character.persona.PersonalityWeight;
import com.lilithsthrone.game.character.persona.SexualOrientation;
import com.lilithsthrone.game.character.quests.Quest;
import com.lilithsthrone.game.character.quests.QuestLine;
import com.lilithsthrone.game.character.quests.QuestType;
import com.lilithsthrone.game.character.race.Race;
import com.lilithsthrone.game.character.race.RaceStage;
import com.lilithsthrone.game.character.race.Subspecies;
import com.lilithsthrone.game.combat.Combat;
import com.lilithsthrone.game.dialogue.utils.UtilText;
import com.lilithsthrone.game.inventory.CharacterInventory;
import com.lilithsthrone.game.inventory.ShopTransaction;
import com.lilithsthrone.game.inventory.weapon.AbstractWeaponType;
import com.lilithsthrone.game.occupantManagement.HuntingContract;
import com.lilithsthrone.main.Main;
import com.lilithsthrone.utils.Colour;
import com.lilithsthrone.utils.SizedStack;
import com.lilithsthrone.utils.TreeNode;
import com.lilithsthrone.utils.XMLSaving;
import com.lilithsthrone.world.WorldType;
import com.lilithsthrone.world.places.PlaceType;

/**
 * @since 0.1.0
 * @version 0.2.11
 * @author Innoxia
 */
public class PlayerCharacter extends GameCharacter implements XMLSaving {
	
	private String title;
	
	private int karma;

	private Map<QuestLine, Quest> quests;

	private boolean mainQuestUpdated, sideQuestUpdated, relationshipQuestUpdated;

	private Set<Subspecies> racesDiscoveredFromBook;
	
	protected List<String> friendlyOccupants;
	
	// Servants' Hall Hunt contracts
	private List<HuntingContract> huntingContracts;
	
	private HuntingContract activeContract;
	
	// Trader buy-back:
	private SizedStack<ShopTransaction> buybackStack;

	private List<String> charactersEncountered;
	
	public PlayerCharacter(NameTriplet nameTriplet, int level, LocalDateTime birthday, Gender gender, Subspecies startingSubspecies, RaceStage stage, CharacterInventory inventory, WorldType startingWorld, PlaceType startingPlace) {
		super(nameTriplet, "", level, Main.game.getDateNow().minusYears(22), gender, startingSubspecies, stage, new CharacterInventory(0), startingWorld, startingPlace);

		this.setSexualOrientation(SexualOrientation.AMBIPHILIC);
		
		title = "The Human";
		
		karma = 0;
		
		for(PersonalityTrait trait : PersonalityTrait.values()) {
			this.setPersonalityTrait(trait, PersonalityWeight.AVERAGE);
		}
		
		this.setMaxCompanions(1);
		
		quests = new EnumMap<>(QuestLine.class);

		mainQuestUpdated = false;
		sideQuestUpdated = false;
		relationshipQuestUpdated = false;
		
		racesDiscoveredFromBook = new HashSet<>();

		buybackStack = new SizedStack<>(24);

		charactersEncountered = new ArrayList<>();

		friendlyOccupants = new ArrayList<>();
		
		huntingContracts = null;
		activeContract = null;
		
		this.setAttribute(Attribute.MAJOR_PHYSIQUE, 10f, false);
		this.setAttribute(Attribute.MAJOR_ARCANE, 0f, false);
		this.setAttribute(Attribute.MAJOR_CORRUPTION, 0f, false);
	}
	
	@Override
	public boolean isUnique() {
		return true;
	}
	
	@Override
	public Element saveAsXML(Element parentElement, Document doc) {
		Element properties = super.saveAsXML(parentElement, doc);
		
		Element playerSpecific = doc.createElement("playerSpecific");
		properties.appendChild(playerSpecific);
		
		CharacterUtils.createXMLElementWithValue(doc, playerSpecific, "title", this.getTitle());
		CharacterUtils.createXMLElementWithValue(doc, playerSpecific, "karma", String.valueOf(this.getKarma()));
		
		Element questUpdatesElement = doc.createElement("questUpdates");
		playerSpecific.appendChild(questUpdatesElement);
		CharacterUtils.createXMLElementWithValue(doc, playerSpecific, "mainQuestUpdated", String.valueOf(this.mainQuestUpdated));
		CharacterUtils.createXMLElementWithValue(doc, playerSpecific, "sideQuestUpdated", String.valueOf(this.sideQuestUpdated));
		CharacterUtils.createXMLElementWithValue(doc, playerSpecific, "relationshipQuestUpdated", String.valueOf(this.relationshipQuestUpdated));
		
		Element innerElement = doc.createElement("racesDiscovered");
		playerSpecific.appendChild(innerElement);
		for(Subspecies subspecies : racesDiscoveredFromBook) {
			if(subspecies != null) {
				CharacterUtils.createXMLElementWithValue(doc, innerElement, "race", subspecies.toString());
			}
		}
		
		Element charactersEncounteredElement = doc.createElement("charactersEncountered");
		playerSpecific.appendChild(charactersEncounteredElement);
		for(String id : charactersEncountered) {
			CharacterUtils.createXMLElementWithValue(doc, charactersEncounteredElement, "id", id);
		}
		// TODO : Make a function to translate a contract to XML to clear code. I'll do it Inno no worry! -Max Nobody
	        Element slaveHuntElement = doc.createElement("slaveHunts");
        	playerSpecific.appendChild(slaveHuntElement);
        	Element contractElement;
        	int i = 0;
        	for(HuntingContract contract : this.getAvailableContracts()) {
        		contractElement = doc.createElement("availableContract");
        		slaveHuntElement.appendChild(contractElement);
        		CharacterUtils.addAttribute(doc, contractElement, "level", String.valueOf(contract.getLevel()));
			CharacterUtils.addAttribute(doc, contractElement, "obedienceMin", String.valueOf(contract.getObedienceMin()));
			CharacterUtils.addAttribute(doc, contractElement, "obedienceMax", String.valueOf(contract.getObedienceMax()));
        		CharacterUtils.addAttribute(doc, contractElement, "heightMin", String.valueOf(contract.getHeightMin()));
        		CharacterUtils.addAttribute(doc, contractElement, "heightMax", String.valueOf(contract.getHeightMax()));
        		CharacterUtils.addAttribute(doc, contractElement, "pussyMin", String.valueOf(contract.getPussyMin()));
        		CharacterUtils.addAttribute(doc, contractElement, "pussyMax", String.valueOf(contract.getPussyMax()));
        		CharacterUtils.addAttribute(doc, contractElement, "anusMin", String.valueOf(contract.getAnusMin()));
            		CharacterUtils.addAttribute(doc, contractElement, "anusMax", String.valueOf(contract.getAnusMax()));
        		CharacterUtils.addAttribute(doc, contractElement, "penisMin", String.valueOf(contract.getPenisMin()));
        		CharacterUtils.addAttribute(doc, contractElement, "penisMax", String.valueOf(contract.getPenisMin()));
        		CharacterUtils.addAttribute(doc, contractElement, "valueMultiplier", String.valueOf(contract.getValueMultiplier()));
			CharacterUtils.addAttribute(doc, contractElement, "vaginalVirgin", String.valueOf(contract.getVaginalVirgin()));
			CharacterUtils.addAttribute(doc, contractElement, "analVirgin", String.valueOf(contract.getAnalVirgin()));
			CharacterUtils.addAttribute(doc, contractElement, "oralVirgin", String.valueOf(contract.getOralVirgin()));
			CharacterUtils.addAttribute(doc, contractElement, "race", String.valueOf(contract.getRace()));
			CharacterUtils.addAttribute(doc, contractElement, "skinColour", String.valueOf(contract.getSkinColour()));
			CharacterUtils.addAttribute(doc, contractElement, "eyeColour", String.valueOf(contract.getEyeColour()));
			CharacterUtils.addAttribute(doc, contractElement, "hairColour", String.valueOf(contract.getHairColour()));
			CharacterUtils.addAttribute(doc, contractElement, "bodySize", String.valueOf(contract.getBodySize()));
			CharacterUtils.addAttribute(doc, contractElement, "muscle", String.valueOf(contract.getMuscle()));
			CharacterUtils.addAttribute(doc, contractElement, "gender", String.valueOf(contract.getGender()));
			CharacterUtils.addAttribute(doc, contractElement, "cupSizeMin", String.valueOf(contract.getCupSizeMin()));
			CharacterUtils.addAttribute(doc, contractElement, "cupSizeMax", String.valueOf(contract.getCupSizeMax()));
            		while (contract.getFetishes() != null && i < contract.getFetishes().size()) {
				CharacterUtils.addAttribute(doc, contractElement, "fetish"+i, String.valueOf(contract.getFetishes().get(i)));
				i++;
			}
			CharacterUtils.addAttribute(doc, contractElement, "derivedFetish", String.valueOf(contract.getDerivedFetish()));
			CharacterUtils.addAttribute(doc, contractElement, "sexualOrientation", String.valueOf(contract.getSexualOrientation()));
		}
        	HuntingContract activeContract = null;
        	if ((activeContract = this.getActiveContract()) != null) {
			contractElement = doc.createElement("activeContract");
			slaveHuntElement.appendChild(contractElement);
			CharacterUtils.addAttribute(doc, contractElement, "level", String.valueOf(activeContract.getLevel()));
			CharacterUtils.addAttribute(doc, contractElement, "obedienceMin", String.valueOf(activeContract.getObedienceMin()));
			CharacterUtils.addAttribute(doc, contractElement, "obedienceMax", String.valueOf(activeContract.getObedienceMax()));
			CharacterUtils.addAttribute(doc, contractElement, "heightMin", String.valueOf(activeContract.getHeightMin()));
			CharacterUtils.addAttribute(doc, contractElement, "heightMax", String.valueOf(activeContract.getHeightMax()));
			CharacterUtils.addAttribute(doc, contractElement, "pussyMin", String.valueOf(activeContract.getPussyMin()));
			CharacterUtils.addAttribute(doc, contractElement, "pussyMax", String.valueOf(activeContract.getPussyMax()));
			CharacterUtils.addAttribute(doc, contractElement, "anusMin", String.valueOf(activeContract.getAnusMin()));
			CharacterUtils.addAttribute(doc, contractElement, "anusMax", String.valueOf(activeContract.getAnusMax()));
			CharacterUtils.addAttribute(doc, contractElement, "penisMin", String.valueOf(activeContract.getPenisMin()));
			CharacterUtils.addAttribute(doc, contractElement, "penisMax", String.valueOf(activeContract.getPenisMin()));
			CharacterUtils.addAttribute(doc, contractElement, "valueMultiplier", String.valueOf(activeContract.getValueMultiplier()));
			CharacterUtils.addAttribute(doc, contractElement, "vaginalVirgin", String.valueOf(activeContract.getVaginalVirgin()));
			CharacterUtils.addAttribute(doc, contractElement, "analVirgin", String.valueOf(activeContract.getAnalVirgin()));
			CharacterUtils.addAttribute(doc, contractElement, "oralVirgin", String.valueOf(activeContract.getOralVirgin()));
			CharacterUtils.addAttribute(doc, contractElement, "race", String.valueOf(activeContract.getRace()));
			CharacterUtils.addAttribute(doc, contractElement, "skinColour", String.valueOf(activeContract.getSkinColour()));
			CharacterUtils.addAttribute(doc, contractElement, "eyeColour", String.valueOf(activeContract.getEyeColour()));
			CharacterUtils.addAttribute(doc, contractElement, "hairColour", String.valueOf(activeContract.getHairColour()));
			CharacterUtils.addAttribute(doc, contractElement, "bodySize", String.valueOf(activeContract.getBodySize()));
			CharacterUtils.addAttribute(doc, contractElement, "muscle", String.valueOf(activeContract.getMuscle()));
			CharacterUtils.addAttribute(doc, contractElement, "gender", String.valueOf(activeContract.getGender()));
			CharacterUtils.addAttribute(doc, contractElement, "cupSizeMin", String.valueOf(activeContract.getCupSizeMin()));
			CharacterUtils.addAttribute(doc, contractElement, "cupSizeMax", String.valueOf(activeContract.getCupSizeMax()));
			while (activeContract.getFetishes() != null && i < activeContract.getFetishes().size()) {
				CharacterUtils.addAttribute(doc, contractElement, "fetish"+i, String.valueOf(activeContract.getFetishes().get(i)));
				i++;
			}
			CharacterUtils.addAttribute(doc, contractElement, "derivedFetish", String.valueOf(activeContract.getDerivedFetish()));
			CharacterUtils.addAttribute(doc, contractElement, "sexualOrientation", String.valueOf(activeContract.getSexualOrientation()));
		}
		
		innerElement = doc.createElement("questMap");
		playerSpecific.appendChild(innerElement);
		for(Entry<QuestLine, Quest> entry : quests.entrySet()) {
			Element e = doc.createElement("entry");
			innerElement.appendChild(e);
			CharacterUtils.addAttribute(doc, e, "questLine", entry.getKey().toString());
			CharacterUtils.addAttribute(doc, e, "quest", String.valueOf(entry.getValue()));
		}
		
		Element friendlyOccupants = doc.createElement("friendlyOccupants");
		playerSpecific.appendChild(friendlyOccupants);
		for(String occupant : this.getFriendlyOccupants()) {
			Element element = doc.createElement("occupant");
			friendlyOccupants.appendChild(element);
			
			CharacterUtils.addAttribute(doc, element, "id", occupant);
		}
		
//		private SizedStack<ShopTransaction> buybackStack; TODO
		
//		Element slavesOwned = doc.createElement("slavesExported");
//		properties.appendChild(slavesOwned);
//		for(String id : this.getSlavesOwned()) {
//			Main.game.getNPCById(id).saveAsXML(slavesOwned, doc);
//		}
		
		return properties;
	}
	
	public static PlayerCharacter loadFromXML(StringBuilder log, Element parentElement, Document doc, CharacterImportSetting... settings) {
		PlayerCharacter character = new PlayerCharacter(new NameTriplet(""), 0, null, Gender.F_V_B_FEMALE, Subspecies.HUMAN, RaceStage.HUMAN, new CharacterInventory(0), WorldType.DOMINION, PlaceType.DOMINION_AUNTS_HOME);
		
		GameCharacter.loadGameCharacterVariablesFromXML(character, log, parentElement, doc, settings);

		NodeList nodes = parentElement.getElementsByTagName("core");
		Element element = (Element) nodes.item(0);
		String version = "";
		if(element.getElementsByTagName("version").item(0)!=null) {
			version = ((Element) element.getElementsByTagName("version").item(0)).getAttribute("value");
		}
		
		Element playerSpecificElement = (Element) parentElement.getElementsByTagName("playerSpecific").item(0);
		
		if(playerSpecificElement!=null) {
			if(playerSpecificElement.getElementsByTagName("title").getLength()!=0) {
				character.setTitle(((Element)playerSpecificElement.getElementsByTagName("title").item(0)).getAttribute("value"));
			}
			
			if(playerSpecificElement.getElementsByTagName("karma").getLength()!=0) {
				character.setKarma(Integer.valueOf(((Element)playerSpecificElement.getElementsByTagName("karma").item(0)).getAttribute("value")));
			}
			
			if(playerSpecificElement.getElementsByTagName("mainQuestUpdated").getLength()!=0) {
				character.setMainQuestUpdated(Boolean.valueOf(((Element)playerSpecificElement.getElementsByTagName("mainQuestUpdated").item(0)).getAttribute("value")));
			}
			if(playerSpecificElement.getElementsByTagName("sideQuestUpdated").getLength()!=0) {
				character.setSideQuestUpdated(Boolean.valueOf(((Element)playerSpecificElement.getElementsByTagName("sideQuestUpdated").item(0)).getAttribute("value")));
			}
			if(playerSpecificElement.getElementsByTagName("relationshipQuestUpdated").getLength()!=0) {
				character.setRelationshipQuestUpdated(Boolean.valueOf(((Element)playerSpecificElement.getElementsByTagName("relationshipQuestUpdated").item(0)).getAttribute("value")));
			}
			if(playerSpecificElement.getElementsByTagName("availableContract").getLength()!=0) {
				int i = 0;
				while (i < playerSpecificElement.getElementsByTagName("availableContract").getLength()) {
					character.addAvailableContractFromXMLValues((Element)playerSpecificElement.getElementsByTagName("availableContract").item(i));
					i++;
				}
			}
			if(playerSpecificElement.getElementsByTagName("activeContract").getLength() != 0) {
				character.addActiveContractFromXMLValues ((Element)playerSpecificElement.getElementsByTagName("activeContract").item(0));
			}
	
			try {
				Element racesDiscoveredElement = (Element) playerSpecificElement.getElementsByTagName("racesDiscovered").item(0);
				if(racesDiscoveredElement != null) {
					
					NodeList races = racesDiscoveredElement.getElementsByTagName("race");
					for(int i=0; i < races.getLength(); i++){
						Element e = (Element) races.item(i);
						try {
							character.addRaceDiscoveredFromBook(Subspecies.valueOf(e.getAttribute("value")));
						} catch(Exception ex) {
						}
					}
				}
			} catch(Exception ex) {
			}
			
			Element charactersEncounteredElement = (Element) playerSpecificElement.getElementsByTagName("charactersEncountered").item(0);
			if(charactersEncounteredElement != null) {
				NodeList charactersEncounteredIds = charactersEncounteredElement.getElementsByTagName("id");
				for(int i=0; i<charactersEncounteredIds.getLength(); i++){
					Element e = (Element) charactersEncounteredIds.item(i);
					character.addCharacterEncountered(e.getAttribute("value"));
				}
			}
			
			Element questMapElement = (Element) playerSpecificElement.getElementsByTagName("questMap").item(0);
			if(questMapElement!=null) {
				NodeList questMapEntries = questMapElement.getElementsByTagName("entry");
				if(Main.isVersionOlderThan(version, "0.1.99.5")) {
				
					for(int i=0; i< questMapEntries.getLength(); i++){
						Element e = (Element) questMapEntries.item(i);
						
						try {
							int progress = Integer.valueOf(e.getAttribute("progress"));
							QuestLine questLine = QuestLine.valueOf(e.getAttribute("questLine"));
							TreeNode<Quest> q = questLine.getQuestTree();
							
							for(int it=0;it<progress;it++) {
								if(!q.getChildren().isEmpty()) {
									q = q.getChildren().get(0);
								}
							}
							
//							// Add one if quest is complete: (This is due to adding a 'complete quest' at the end of each quest line.)
//							if(questLine!=QuestLine.MAIN && !q.getChildren().isEmpty() && q.getChildren().get(0).getChildren().isEmpty()) {
//								q = q.getChildren().get(0);
//							}
							
							character.quests.put(
									questLine,
									q.getData());
							
						} catch(Exception ex) {
							System.err.println("ERR Quest!");
						}
					}
				} else {
					for(int i=0; i<questMapEntries.getLength(); i++){
						Element e = (Element) questMapEntries.item(i);
						try {
							String questLine = e.getAttribute("questLine");
							if(questLine.contains("SIDE_NYAN")) {
								questLine = questLine.replace("SIDE_NYAN", "RELATIONSHIP_NYAN");
							}
							
							String quest = e.getAttribute("quest");
							if(quest.contains("SIDE_NYAN")) {
								quest = quest.replace("SIDE_NYAN", "RELATIONSHIP_NYAN");
							}
							character.quests.put(
									QuestLine.valueOf(questLine),
									Quest.valueOf(quest));
						} catch(Exception ex) {
						}
					}
				}
			}
		}
		
		try {
			for(int i=0; i<((Element) playerSpecificElement.getElementsByTagName("friendlyOccupants").item(0)).getElementsByTagName("occupant").getLength(); i++){
				Element e = ((Element)playerSpecificElement.getElementsByTagName("occupant").item(i));
				
				if(!e.getAttribute("id").equals("NOT_SET")) {
					character.getFriendlyOccupants().add(e.getAttribute("id"));
					CharacterUtils.appendToImportLog(log, "<br/>Added occupant: "+e.getAttribute("id"));
				}
			}
		} catch(Exception ex) {	
		}
		
//		// Slaves:
//		
//		Element slavesOwned = (Element) parentElement.getElementsByTagName("slavesExported").item(0);
//		if(slavesOwned!=null) {
//			for(int i=0; i< slavesOwned.getElementsByTagName("character").getLength(); i++){
//				Element e = ((Element)slavesOwned.getElementsByTagName("character").item(i));
//				
//				SlaveImport slave = SlaveImport.loadFromXML2(log, e, doc);
//				
//				//TODO move into slave's import:
//				slave.setMana(slave.getAttributeValue(Attribute.MANA_MAXIMUM));
//				slave.setHealth(slave.getAttributeValue(Attribute.HEALTH_MAXIMUM));
//				slave.setStamina(slave.getAttributeValue(Attribute.STAMINA_MAXIMUM));
//				
//				try {
//					Main.game.getSlaveImports().add(slave);
////					character.addSlave(slave);
//					slave.setLocation(WorldType.SLAVER_ALLEY, PlaceType.SLAVER_ALLEY_SLAVERY_ADMINISTRATION, true);
//					
//				} catch (Exception e1) {
//					e1.printStackTrace();
//				}
//			}
//		}
		
		
		return character;
	}

	private void addAvailableContractFromXMLValues(Element element) {
        	int contractID;
        	if (this.getAvailableContracts() == null) {
			this.setAvailableContracts(new ArrayList<HuntingContract>());
			this.getAvailableContracts().add(new HuntingContract());
			contractID = 0;
		} else {
			contractID = this.getAvailableContracts().size();
			this.getAvailableContracts().add(new HuntingContract());
		}
		this.getAvailableContracts().get(contractID).setAnalVirgin(Boolean.valueOf(element.getAttribute("analVirgin")));
		this.getAvailableContracts().get(contractID).setAnusMax(Integer.valueOf(element.getAttribute("anusMax")));
		this.getAvailableContracts().get(contractID).setAnusMin(Integer.valueOf(element.getAttribute("anusMin")));
		this.getAvailableContracts().get(contractID).setBodySize(BodySize.getEnum(element.getAttribute("bodySize")));
		this.getAvailableContracts().get(contractID).setCupSizeMax(CupSize.getEnum(element.getAttribute("cupSizeMax")));
		this.getAvailableContracts().get(contractID).setCupSizeMin(CupSize.getEnum(element.getAttribute("cupSizeMin")));
		if (!element.getAttribute("derivedFetish").equals("null")) {
			this.getAvailableContracts().get(contractID).setDerivedFetish(Fetish.getEnum(element.getAttribute("derivedFetish")));
		} else {
			this.getAvailableContracts().get(contractID).setDerivedFetish(null);
		}
		this.getAvailableContracts().get(contractID).setEyeColour(Colour.getEnum(element.getAttribute("eyeColour")));
		int i = 0;
		while (element.getAttribute("fetish"+i) != "") {
			if (i == 0) {
				this.getAvailableContracts().get(contractID).setFetishes(new ArrayList<Fetish>());
			}
			this.getAvailableContracts().get(contractID).getFetishes().add(Fetish.getEnum(element.getAttribute("fetish"+i)));
			i++;
		}
		this.getAvailableContracts().get(contractID).setGender(Gender.getEnum(element.getAttribute("gender")));
		this.getAvailableContracts().get(contractID).setHairColour(Colour.getEnum(element.getAttribute("hairColour")));
		this.getAvailableContracts().get(contractID).setHeightMax(Integer.valueOf(element.getAttribute("heightMax")));
		this.getAvailableContracts().get(contractID).setHeightMin(Integer.valueOf(element.getAttribute("heightMin")));
		this.getAvailableContracts().get(contractID).setLevel(Integer.valueOf(element.getAttribute("level")));
		this.getAvailableContracts().get(contractID).setMuscle(Muscle.getEnum(element.getAttribute("muscle")));
		this.getAvailableContracts().get(contractID).setObedienceMax(Integer.valueOf(element.getAttribute("obedienceMax")));
		this.getAvailableContracts().get(contractID).setObedienceMin(Integer.valueOf(element.getAttribute("obedienceMin")));
		this.getAvailableContracts().get(contractID).setOralVirgin(Boolean.valueOf(element.getAttribute("oralVirgin")));
		this.getAvailableContracts().get(contractID).setPenisMax(Integer.valueOf(element.getAttribute("penisMax")));
		this.getAvailableContracts().get(contractID).setPenisMin(Integer.valueOf(element.getAttribute("penisMin")));
		this.getAvailableContracts().get(contractID).setPussyMax(Integer.valueOf(element.getAttribute("pussyMax")));
		this.getAvailableContracts().get(contractID).setPussyMin(Integer.valueOf(element.getAttribute("pussyMin")));
		this.getAvailableContracts().get(contractID).setRace(Race.getEnum(element.getAttribute("race")));
		this.getAvailableContracts().get(contractID).setSexualOrientation(SexualOrientation.getEnum(element.getAttribute("sexualOrientation")));
		this.getAvailableContracts().get(contractID).setSkinColour(Colour.getEnum(element.getAttribute("skinColour")));
		this.getAvailableContracts().get(contractID).setVaginalVirgin(Boolean.valueOf(element.getAttribute("vaginalVirgin")));
		this.getAvailableContracts().get(contractID).setValueMultiplier(Double.valueOf(element.getAttribute("valueMultiplier")));
	}
    
	private void addActiveContractFromXMLValues(Element element) {
		this.setActiveContract(new HuntingContract());
		this.getActiveContract().setAnalVirgin(Boolean.valueOf(element.getAttribute("analVirgin")));
		this.getActiveContract().setAnusMax(Integer.valueOf(element.getAttribute("anusMax")));
		this.getActiveContract().setAnusMin(Integer.valueOf(element.getAttribute("anusMin")));
		this.getActiveContract().setBodySize(BodySize.getEnum(element.getAttribute("bodySize")));
		this.getActiveContract().setCupSizeMax(CupSize.getEnum(element.getAttribute("cupSizeMax")));
		this.getActiveContract().setCupSizeMin(CupSize.getEnum(element.getAttribute("cupSizeMin")));
		if (!element.getAttribute("derivedFetish").equals("null")) {
			this.getActiveContract().setDerivedFetish(Fetish.getEnum(element.getAttribute("derivedFetish")));
		} else {
			this.getActiveContract().setDerivedFetish(null);
		}
		this.getActiveContract().setEyeColour(Colour.getEnum(element.getAttribute("eyeColour")));
		int i = 0;
		while (element.getAttribute("fetish"+i) != "") {
			if (i == 0) {
				this.getActiveContract().setFetishes(new ArrayList<Fetish>());
			}
			this.getActiveContract().getFetishes().add(Fetish.getEnum(element.getAttribute("fetish"+i)));
			i++;
		}
		this.getActiveContract().setGender(Gender.getEnum(element.getAttribute("gender")));
		this.getActiveContract().setHairColour(Colour.getEnum(element.getAttribute("hairColour")));
		this.getActiveContract().setHeightMax(Integer.valueOf(element.getAttribute("heightMax")));
		this.getActiveContract().setHeightMin(Integer.valueOf(element.getAttribute("heightMin")));
		this.getActiveContract().setLevel(Integer.valueOf(element.getAttribute("level")));
		this.getActiveContract().setMuscle(Muscle.getEnum(element.getAttribute("muscle")));
		this.getActiveContract().setObedienceMax(Integer.valueOf(element.getAttribute("obedienceMax")));
		this.getActiveContract().setObedienceMin(Integer.valueOf(element.getAttribute("obedienceMin")));
		this.getActiveContract().setOralVirgin(Boolean.valueOf(element.getAttribute("oralVirgin")));
		this.getActiveContract().setPenisMax(Integer.valueOf(element.getAttribute("penisMax")));
		this.getActiveContract().setPenisMin(Integer.valueOf(element.getAttribute("penisMin")));
		this.getActiveContract().setPussyMax(Integer.valueOf(element.getAttribute("pussyMax")));
		this.getActiveContract().setPussyMin(Integer.valueOf(element.getAttribute("pussyMin")));
		this.getActiveContract().setRace(Race.getEnum(element.getAttribute("race")));
		this.getActiveContract().setSexualOrientation(SexualOrientation.getEnum(element.getAttribute("sexualOrientation")));
		this.getActiveContract().setSkinColour(Colour.getEnum(element.getAttribute("skinColour")));
		this.getActiveContract().setVaginalVirgin(Boolean.valueOf(element.getAttribute("vaginalVirgin")));
		this.getActiveContract().setValueMultiplier(Double.valueOf(element.getAttribute("valueMultiplier")));
	}
	
	@Override
	protected void updateAttributeListeners() {
		if (playerAttributeChangeEventListeners != null)
			for (CharacterChangeEventListener eventListener : playerAttributeChangeEventListeners)
				eventListener.onChange();
	}

	@Override
	protected void updateLocationListeners() {
		if (playerLocationChangeEventListeners != null)
			for (CharacterChangeEventListener eventListener : playerLocationChangeEventListeners)
				eventListener.onChange();
	}

	@Override
	public void updateInventoryListeners() {
		if (playerInventoryChangeEventListeners != null)
			for (CharacterChangeEventListener eventListener : playerInventoryChangeEventListeners)
				eventListener.onChange();
	}
	
	@Override
	public String getId() {
		return "PlayerCharacter";//-"+Main.game.getNpcTally();
	}
	
	@Override
	public boolean isPlayer() {
		return true;
	}
	
	@Override
	public int getAppearsAsAgeValue() {
		if(Main.game.isInNewWorld()) {
			return getAgeValue() - Game.TIME_SKIP_YEARS;
		}
		return getAgeValue();
	}

	@Override
	public int getAgeValue() {
		if(Main.game.isInNewWorld()) {
			return super.getAgeValue();
		} else {
			return (int) ChronoUnit.YEARS.between(birthday, Main.game.getDateNow().minusYears(Game.TIME_SKIP_YEARS));
		}
	}
	
	@Override
	public String getBodyDescription() {
		return body.getDescription(this);
	}

	@Override
	public String getDescription() {
		if(!Main.game.isInNewWorld()) {
			return ""; // This isn't displayed anywhere before the game starts for real.
		} else {
			return "Having been pulled into an enchanted mirror in your aunt Lily's museum, you woke up to find yourself in another world."
					+ " By a stroke of good fortune, one of the first people you met was Lilaya; this world's version of your aunt."
					+ " Having convinced her that your story is true, you're now working towards finding a way to get back to your old world.";
		}
	}
	
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getKarma() {
		return karma;
	}

	public void setKarma(int karma) {
		this.karma = karma;
	}
	
	/**
	 * This is just an internal stat that isn't used for anything, other than to help me feel better about writing horrible scenes.<br/><br/>
	 * 
	 * -100 would be for something huge, like attacking and enslaving one of your children.<br/>
	 * -10 would be for something large, like stealing from someone.<br/>
	 * -1 would be for something small, like insulting someone who doesn't deserve it.<br/>
	 * 0 = neutral<br/>
	 * +1 would be for something small, like giving a gift.<br/>
	 * +10 would be for something large, like sacrificing your own well-being to help another person.<br/>
	 * +100 would be for something huge, like buying and then immediately freeing a slave.<br/>
	 * @param increment
	 */
	public void incrementKarma(int increment) {
		this.karma += increment;
	}
	
	public List<HuntingContract> getAvailableContracts() {
        	return this.huntingContracts;
	}
    
	public HuntingContract getActiveContract() {
        	return this.activeContract;
	}
    
	public void setAvailableContracts(List<HuntingContract> huntingContracts) {
        	this.huntingContracts = huntingContracts;
	}
    
    	public void setActiveContract(HuntingContract activeContract) {
        	this.activeContract = activeContract;
    	}
    
    	// Choose the contracts from the list of possibles contracts
    	public void setActiveContract(int id) {
        	if (this.huntingContracts.get(id) != null) {
            		this.activeContract = this.huntingContracts.get(id);
        	}
    	}
	
	@Override
	public boolean isRelatedTo(GameCharacter character) {
		if(character.equals(Main.game.getLilaya())) {
			return true;
		}
		return super.isRelatedTo(character);
	}
	
	// Quests:

	public void resetAllQuests() {
		quests = new EnumMap<>(QuestLine.class);
	}
	
	public boolean isMainQuestUpdated() {
		return mainQuestUpdated;
	}

	public void setMainQuestUpdated(boolean mainQuestUpdated) {
		this.mainQuestUpdated = mainQuestUpdated;
	}

	public boolean isSideQuestUpdated() {
		return sideQuestUpdated;
	}

	public void setSideQuestUpdated(boolean sideQuestUpdated) {
		this.sideQuestUpdated = sideQuestUpdated;
	}

	public boolean isRelationshipQuestUpdated() {
		return relationshipQuestUpdated;
	}

	public void setRelationshipQuestUpdated(boolean relationshipQuestUpdated) {
		this.relationshipQuestUpdated = relationshipQuestUpdated;
	}

	public String startQuest(QuestLine questLine) {
		return setQuestProgress(questLine, questLine.getQuestTree().getData());
	}
	
	public String setQuestProgress(QuestLine questLine, Quest quest) {
		if(!questLine.getQuestTree().childrenContainsData(quest)) {
			System.err.println("QuestTree in quest line "+questLine+" does not contain quest: "+quest);
			return "";
		}
		
		if (questLine.getType() == QuestType.MAIN) {
			setMainQuestUpdated(true);
			
		} else if (questLine.getType() == QuestType.SIDE) {
			setSideQuestUpdated(true);
			
		} else {
			setRelationshipQuestUpdated(true);
		}
		
		
		if(quests.containsKey(questLine)) {
			Quest currentQuest = questLine.getQuestTree().getFirstNodeWithData(quests.get(questLine)).getData();
			
			String experienceUpdate = incrementExperience(currentQuest.getExperienceReward(), true);
			
			quests.put(questLine, quest);
			
			if (questLine.getQuestTree().getFirstNodeWithData(quest).getChildren().isEmpty()) { // QuestLine complete (No more children in the tree)
				return "<p style='text-align:center;'>"
						+ "<b style='color:" + questLine.getType().getColour().toWebHexString() + ";'>Quest - " + questLine.getName() + "</b><br/>"
						+ "<b style='color:"+Colour.GENERIC_GOOD.toWebHexString()+";'>Task Completed</b><b> - "+currentQuest.getName()+"</b><br/>"
						+ "<b>All Tasks Completed!</b></p>"
						+ experienceUpdate;
			} else {
				return "<p style='text-align:center;'>"
						+ "<b style='color:" + questLine.getType().getColour().toWebHexString() + ";'>Quest - " + questLine.getName() + "</b><br/>"
						+ "<b style='color:"+Colour.GENERIC_GOOD.toWebHexString()+";'>Task Completed</b><br/>"
						+ "<b>New Task - " + quest.getName() + "</b></p>"
						+ experienceUpdate;
			}
			
		} else {
			quests.put(questLine, quest);
			
			return "<p style='text-align:center;'>"
					+ "<b style='color:" + questLine.getType().getColour().toWebHexString() + ";'>New Quest - " + questLine.getName() + "</b><br/>"
					+ "<b>New Task - " + quest.getName() + "</b></p>";
		}
		
	}
	
	public Map<QuestLine, Quest> getQuests() {
		return quests;
	}
	
	public Quest getQuest(QuestLine questLine) {
		return quests.get(questLine);
	}
	
	public boolean hasQuest(QuestLine questLine) {
		return quests.containsKey(questLine);
	}

	public boolean isQuestCompleted(QuestLine questLine) {
		if(!hasQuest(questLine)) {
			return false;
		}
		return questLine.getQuestTree().getFirstNodeWithData(quests.get(questLine)).getChildren().isEmpty();
	}
	
	public boolean isHasSlaverLicense() {
		return isQuestCompleted(QuestLine.SIDE_SLAVERY) || Main.game.isDebugMode();
	}
	
	public boolean isAbleToAccessRoomManagement() {
		return isHasSlaverLicense() || isQuestCompleted(QuestLine.SIDE_ACCOMMODATION);
	}
	
	public boolean isQuestProgressGreaterThan(QuestLine questLine, Quest quest) {
		if(!hasQuest(questLine)) {
			System.err.println("Player does not have Quest: "+quest.toString());
			return false;
		}
		
		if(questLine.getQuestTree().getFirstNodeWithData(quest)==null) {
			System.err.println("Quest "+quest.toString()+" was not in QuestLine!");
			return false;
		}
		
		// Check to see if the current progress does not have a child with quest data:
		return questLine.getQuestTree().getFirstNodeWithData(getQuest(questLine)).getFirstNodeWithData(quest)==null;
	}
	
	public boolean isQuestProgressLessThan(QuestLine questLine, Quest quest) {
		if(!hasQuest(questLine)) {
			System.err.println("Player does not have Quest: "+quest.toString());
			return false;
		}
		
		if(getQuest(questLine)==quest) {
			return false;
		}
		
		if(questLine.getQuestTree().getFirstNodeWithData(quest)==null) {
			System.err.println("Quest "+quest.toString()+" was not in QuestLine!");
			return false;
		}
		
		// Check to see if the current progress has a child with quest data:
		return questLine.getQuestTree().getFirstNodeWithData(getQuest(questLine)).getFirstNodeWithData(quest)!=null;
	}

	// Other stuff:

	public List<String> getCharactersEncountered() {
		return charactersEncountered;
	}

	public void addCharacterEncountered(String character) {
		if (!charactersEncountered.contains(character)) {
			charactersEncountered.add(character);
		}
		if(Main.game.isStarted()) {
			sortCharactersEncountered();
		}
	}
	
	public void addCharacterEncountered(GameCharacter character) {
		if (!charactersEncountered.contains(character.getId())) {
			charactersEncountered.add(character.getId());
		}
		if(Main.game.isStarted()) {
			sortCharactersEncountered();
		}
	}
	
	public List<GameCharacter> getCharactersEncounteredAsGameCharacters() {
		List<GameCharacter> npcsEncountered = new ArrayList<>();
		for(String characterId : charactersEncountered) {
			try {
				GameCharacter npc = Main.game.getNPCById(characterId);
				npcsEncountered.add(npc);
			} catch (Exception e) {
				System.err.println("Main.game.getNPCById("+characterId+") returning null in method: getCharactersEncounteredAsGameCharacters()");
			}
		}
		return npcsEncountered;
	}
	
	public void sortCharactersEncountered() {
		List<GameCharacter> npcsEncountered = new ArrayList<>();
		for(String characterId : charactersEncountered) {
			try {
				GameCharacter npc = Main.game.getNPCById(characterId);
				npcsEncountered.add(npc);
			} catch (Exception e) {
				System.err.println("Main.game.getNPCById("+characterId+") returning null in method: sortCharactersEncountered()");
			}
		}
		npcsEncountered.sort((npc1, npc2) -> npc1 instanceof NPCOffspring
				?(npc2 instanceof NPCOffspring
					?npc1.getName().compareTo(npc2.getName())
					:1)
				:(npc2 instanceof NPCOffspring
						?-1
						:npc1.getName().compareTo(npc2.getName())));
		List<String> sortedIDs = new ArrayList<>();
		for(GameCharacter character : npcsEncountered) {
			sortedIDs.add(character.getId());
		}
		charactersEncountered = sortedIDs;
	}
	
	public SizedStack<ShopTransaction> getBuybackStack() {
		return buybackStack;
	}

	public boolean addRaceDiscoveredFromBook(Subspecies subspecies) {
		return racesDiscoveredFromBook.add(subspecies);
	}
	
	public Set<Subspecies> getRacesDiscoveredFromBook() {
		return racesDiscoveredFromBook;
	}

	@Override
	public String getMainAttackDescription(boolean isHit) {
		if(this.getMainWeapon()!=null) {
			return this.getMainWeapon().getWeaponType().getAttackDescription(this, Combat.getTargetedCombatant(this), isHit);
		} else {
			return AbstractWeaponType.genericMeleeAttackDescription(this, Combat.getTargetedCombatant(this), isHit);
		}
	}

	@Override
	public String getSpellDescription() {
		return "<p>"
				+ UtilText.parse(this,
						UtilText.returnStringAtRandom(
						"Concentrating on harnessing the power of your arcane aura, you thrust your [pc.arm] into mid air and cast a spell!"))
			+ "</p>";
	}

	@Override
	public String getSeductionDescription() {
		String description = "";
		if(this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION)
				|| this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION_POWER_OF_SUGGESTION)
				|| this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION_PROJECTED_TOUCH)) {
			if(this.isFeminine()) {
				return UtilText.parse(Combat.getTargetedCombatant(this),
						UtilText.returnStringAtRandom(
								"You put on a smouldering look, and as your [pc.eyes] meet [npc.namePos], you project an extremely lewd moan into [npc.her] head,"
										+ " [pc.thought(~Aaah!~ "
											+(this.hasVagina()
													?"You're making me so wet!"
													:this.hasPenis()
														?"You're getting me so hard!"
														:"You're turning me on so much!")+")]",
								"You lock your [pc.eyes] with [npc.namePos], and, putting on your most innocent look as you pout at [npc.herHim], you project an echoing moan deep into [npc.her] mind,"
									+ " [pc.thought("+
											(this.hasVagina()
													?"~Mmm!~ Fuck me! ~Aaa!~ My pussy's wet and ready for you!"
													:this.hasPenis()
														?"~Mmm!~ I can't wait to fuck you! ~Aaa!~ You're going to love my cock!"
														:"~Mmm!~ Fuck me! ~Aaa!~ I need you so badly!")+")]",
								(this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION_POWER_OF_SUGGESTION)
										|| this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION_PROJECTED_TOUCH)
										?"You pout innocently at [npc.name], before blowing [npc.herHim] a wet kiss."
												+ " As you straighten back up, you project the feeling of a ghostly pair of wet lips pressing against [npc.her] cheek."
										:"")));
			} else {
				return UtilText.parse(Combat.getTargetedCombatant(this),
						UtilText.returnStringAtRandom(
								"You put on a confident look, and as your [pc.eyes] meet [npc.namePos], you project an extremely lewd groan into [npc.her] head,"
									+ " [pc.thought(~Mmm!~ "
											+(this.hasVagina()
													?"You're making me so wet!"
													:this.hasPenis()
														?"You're getting me so hard!"
														:"You're turning me on so much!")+")]",
								"You lock your [pc.eyes] with [npc.namePos], and, throwing [npc.herHim] a charming smile, you project an echoing groan deep into [npc.her] mind,"
									+ " [pc.thought("+
											(this.hasVagina()
													?"~Mmm!~ Fuck me! ~Aaa!~ My pussy's wet and ready for you!"
													:this.hasPenis()
														?"~Mmm!~ I can't wait to fuck you! You're going to love my cock!"
														:"~Mmm!~ I can't wait to have some fun with you!")+")]",
								(this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION_POWER_OF_SUGGESTION)
										|| this.hasStatusEffect(StatusEffect.TELEPATHIC_COMMUNICATION_PROJECTED_TOUCH)
										?"You throw [npc.name] a charming smile, before winking at [npc.herHim] and striking a heroic pose."
												+ " As you straighten back up, you project the feeling of a ghostly pair of arms pulling [npc.herHim] into a strong, confident embrace."
										:"")));
			}
		}
		
		if(this.isFeminine()) {
			description = UtilText.parse(Combat.getTargetedCombatant(this),
					UtilText.returnStringAtRandom(
					"You blow a kiss at [npc.name] and wink suggestively at [npc.herHim].",
					"Biting your lip and putting on your most smouldering look, you run your hands slowly up your inner thighs.",
					"As you give [npc.name] your most innocent look, you blow [npc.herHim] a little kiss.",
					"Turning around, you let out a playful giggle as you give your [pc.ass+] a slap.",
					"You slowly run your hands up the length of your body, before pouting at [npc.name]."));
			
		} else {
			description = UtilText.parse(Combat.getTargetedCombatant(this),
					UtilText.returnStringAtRandom(
					"You blow a kiss at [npc.name] and wink suggestively at [npc.herHim].",
					"Smiling confidently at [npc.name], you slowly run your hands up your inner thighs.",
					"As you give [npc.name] your most seductive look, you blow [npc.herHim] a little kiss.",
					"Turning around, you let out a playful laugh as you give your [pc.ass+] a slap.",
					"You try to look as commanding as possible as you smirk playfully at [npc.name]."));
		}

		return "<p>"
				+ description
				+ "</p>";
	}

	@Override
	public boolean isAbleToBeImpregnated() {
		return true;
	}

	/**
	 * Returns a list of NPCs either living in Lilaya's house or in an apartment known to the player.
	 */
	public List<String> getFriendlyOccupants() {
		return friendlyOccupants;
	}
	
	public boolean addFriendlyOccupant(NPC occupant) {
		return friendlyOccupants.add(occupant.getId());
	}
	
	public boolean removeFriendlyOccupant(GameCharacter occupant) {
		return friendlyOccupants.remove(occupant.getId());
	}
}
