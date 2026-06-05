package dev.vinciguerra.adrsentinel.web.dto.onunumber;

import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
import dev.vinciguerra.adrsentinel.web.dto.adrclass.AdrClassResponseDTO;

/**
 * Data Transfer Object (DTO) in sola lettura (Read-Only) per l'esposizione dei 
 * metadati logistici, chimici e normativi associati a una specifica materia pericolosa.
 * <p><b>Contesto di Dominio (Anagrafica Normativa ADR):</b></p>
 * Questo record modella la vera e propria "carta d'identità" di una sostanza secondo 
 * le direttive del trasporto internazionale ADR. Aggrega in un'unica struttura piatta 
 * tutte le informazioni necessarie per compilare una Lettera di Vettura, calcolare 
 * le esenzioni normative, determinare le etichette di pericolo (Placards) e pianificare 
 * il routing del veicolo.
 * <p><b>Scelta Architetturale (Java Record & Immutabilità):</b></p>
 * L'utilizzo del costrutto {@code record} (introdotto in Java 14+) garantisce nativamente 
 * l'immutabilità assoluta dell'oggetto e la sua natura <i>Thread-Safe</i>. Essendo un 
 * <i>Response DTO</i> (destinato alla serializzazione JSON verso il Frontend), l'oggetto 
 * viene istanziato dal Service/Mapper e viaggia verso il Controller senza alcun rischio 
 * di mutazione accidentale del suo stato durante il transito.
 * @param onuCode Il Numero ONU (UN Number) a 4 cifre (es. "1202" per Gasolio). Rappresenta 
 * la Business Key internazionale univoca della sostanza.
 * @param name La denominazione ufficiale di trasporto (Proper Shipping Name) della sostanza, 
 * testo legalmente vincolante da riportare sui documenti di viaggio.
 * @param physicalState Lo stato fisico della materia (es. Solido, Liquido, Gas). 
 * Parametro cardine per determinare le modalità di trasporto consentite  (in colli, in cisterna o alla rinfusa).
 * @param kemlerCode Il Numero di Identificazione del Pericolo (Hazard Identification Number), 
 * da esporre nella metà superiore dei pannelli arancioni sul mezzo (es. "33", "X88").
 * @param packingGroup Il Gruppo di Imballaggio (Packing Group - PG). Classifica il grado 
 * di pericolo chimico/fisico della sostanza: I (Alto), II (Medio), III (Basso).
 * @param tunnelRestriction Il codice di restrizione stradale (Tunnel Restriction Code, es. "D/E"). 
 * Impatta in modo determinante sugli algoritmi di routing, vietando il transito in specifiche gallerie autostradali.
 * @param transportCategory La Categoria di Trasporto (valore da 0 a 4). Moltiplicatore matematico 
 * essenziale per il calcolo delle esenzioni parziali (Regola del 1000 - ADR 1.1.3.6).
 * @param adrClass La Classe di Pericolo principale ADR (es. 3 per liquidi infiammabili, 8 per corrosivi). 
 * Guida le procedure di segregazione delle merci e le etichette da apporre sul carico.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record OnuNumberResponseDTO(String onuCode, String name, String physicalState, String kemlerCode,
		String packingGroup, String tunnelRestriction, Integer transportCategory, AdrClassResponseDTO adrClass) {
	
	/**
	 * Factory Method statico per la conversione (Mapping) e l'aggregazione di un'entità 
	 * di dominio {@link OnuNumber} nel suo corrispondente Data Transfer Object {@link OnuNumberResponseDTO}.
	 *
	 * <p><b>Contesto Architetturale (Pattern DTO e Information Hiding):</b></p>
	 * Questo metodo agisce come traduttore tra il livello di persistenza (JPA/Hibernate) 
	 * e il contratto API (Presentation Layer). Incapsula la logica di estrazione dei dati 
	 * dall'anagrafica normativa delle merci pericolose (Numero ONU), garantendo che dettagli 
	 * implementativi del database non vengano mai esposti al client o serializzati per errore.
	 * <p><b>Design Pattern e Tecniche Implementate:</b></p>
	 * <ul>
	 * <li><b>Sicurezza e Robustezza (Guard Clause):</b> L'implementazione adotta una rigorosa 
	 * clausola di salvaguardia iniziale ({@code if(entity == null)}). Questo rende il metodo 
	 * intrinsecamente <i>Null-Safe</i>, prevenendo {@code NullPointerException} durante 
	 * le elaborazioni massive (es. mapping di liste tramite Stream API).</li>
	 * <li><b>Serializzazione Sicura degli Enum (Type Erasure):</b> I campi basati su enumeratori 
	 * (Stato Fisico, Gruppo d'Imballaggio, Restrizione Gallerie) vengono esplicitamente 
	 * convertiti in formato testuale tramite l'invocazione di {@code .name()}. Questa pratica 
	 * disaccoppia il payload JSON dalle classi Enum interne di Java, offrendo una 
	 * serializzazione prevedibile, sicura e compatibile con qualsiasi client esterno.</li>
	 * <li><b>Mapping Annidato (Delegated Resolving):</b> Per la risoluzione del grafo degli oggetti, 
	 * invece di implementare logica duplicata, il metodo delega la costruzione dell'oggetto 
	 * figlio al factory method competente ({@link AdrClassResponseDTO#fromEntity}). 
	 * Questo rispetta il principio DRY (Don't Repeat Yourself) e garantisce coerenza strutturale.</li>
	 * </ul>
	 * @param entity L'istanza dell'entità JPA recuperata dal database, rappresentante 
	 * la "carta d'identità" ADR di una sostanza. Ammette valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link OnuNumberResponseDTO} popolata 
	 * con i dati logistici e normativi, oppure {@code null} se l'entità sorgente era assente.
	 */
	public static OnuNumberResponseDTO fromEntity(OnuNumber entity) {
		if(entity == null)
			return null;
		
		return new OnuNumberResponseDTO(
			entity.getOnuCode(),
			entity.getName(),
			entity.getPhysicalState().name(),
			entity.getKemlerCode(),
			entity.getPackingGroup().name(),
			entity.getTunnelRestriction().name(),
			entity.getTransportCategory(),
			AdrClassResponseDTO.fromEntity(entity.getAdrClass())
		);
	}
}
