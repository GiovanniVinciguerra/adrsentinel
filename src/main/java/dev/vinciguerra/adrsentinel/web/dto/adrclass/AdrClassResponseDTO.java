package dev.vinciguerra.adrsentinel.web.dto.adrclass;

import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;

/**
 * Data Transfer Object (DTO) in uscita per la rappresentazione in sola lettura di una Classe ADR.
 * <p>
 * <b>Ruolo Architetturale (Data Hiding & Payload Optimization):</b><br>
 * Questo record funge da "vetrina" o "passaporto" per l'esposizione dei dati verso il client 
 * (es. un frontend React). Garantisce che l'entità di database originale, con le sue annotazioni JPA 
 * e i potenziali metadati tecnici, non attraversi mai il confine della rete (Boundary Layer). 
 * Ottimizza inoltre le prestazioni dell'API, serializzando in JSON esclusivamente i campi 
 * strettamente necessari per il rendering dell'interfaccia utente.
 * </p>
 * <p>
 * <b>Immutabilità e Sicurezza (Java Record):</b><br>
 * Essendo implementato come {@code record} nativo, questo oggetto è strutturalmente <b>Immutabile</b> 
 * e <b>Thread-Safe</b>. Una volta "forgiato" dal Mapper del Controller, il suo stato è sigillato. 
 * Questo previene qualsiasi alterazione accidentale dei dati durante i processi asincroni o durante 
 * la serializzazione eseguita dalla libreria Jackson.
 * </p>
 * @param classCode il codice alfanumerico ufficiale della normativa ADR (Business Key) 
 * (es. "3", "4.1", "1.4S"). Rappresenta l'identificatore logistico primario.
 * @param description la descrizione estesa e leggibile (Human-Readable) della natura del pericolo 
 * (es. "Liquidi infiammabili"). Questo campo è pre-formattato e pronto 
 * per essere inserito direttamente nei menu a tendina o nelle tabelle operative della UI.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Read-Only Immutable View)
 * @since 1.0
 */
public record AdrClassResponseDTO(String classCode, String description) {
	/**
	 * Factory Method statico per la conversione (Mapping) di un'entità di dominio {@link AdrClass} 
	 * nel suo corrispondente Data Transfer Object {@link AdrClassResponseDTO}.
	 * <p><b>Contesto Architetturale (Pattern DTO e Information Hiding):</b></p>
	 * Questo metodo centralizza e isola la logica di trasformazione dal modello relazionale (JPA) 
	 * al contratto API (Response Payload). Agendo come un traduttore monodirezionale, assicura 
	 * che il layer di presentazione (Controller) o il client esterno non entrino mai in contatto 
	 * diretto con il ciclo di vita dell'ORM (Hibernate), prevenendo le classiche vulnerabilità 
	 * di <i>LazyInitializationException</i> e la serializzazione accidentale di metadati del database.
	 * <p><b>Design Pattern e Sicurezza (Null-Safety / Guard Clause):</b></p>
	 * L'implementazione adotta una rigorosa clausola di salvaguardia iniziale (Guard Clause): 
	 * {@code if(entity == null)}. Questo approccio difensivo rende il metodo intrinsecamente 
	 * <i>Null-Safe</i>. È una caratteristica fondamentale quando il metodo viene invocato 
	 * all'interno di iterazioni funzionali (es. {@code stream().map(AdrClassResponseDTO::fromEntity)}), 
	 * poiché garantisce che eventuali campi nulli nel database non inneschino fatali 
	 * {@code NullPointerException} a runtime, restituendo elegantemente un {@code null} gestibile da Jackson.
	 * @param entity L'istanza dell'entità JPA recuperata dalla base dati, rappresentante 
	 * la classe normativa di pericolo ADR (es. Classe 3, Classe 8). Il parametro ammette valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link AdrClassResponseDTO} popolata 
	 * con i dati estratti dall'entità, oppure {@code null} se l'input fornito era assente.
	 */
	public static AdrClassResponseDTO fromEntity(AdrClass entity) {
		if(entity == null)
			return null;
		
		return new AdrClassResponseDTO(
			entity.getClassCode(),
			entity.getDescription()
		);
	}
}
