package dev.vinciguerra.adrsentinel.web.dto.compatibilityrule;

import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRule;
import dev.vinciguerra.adrsentinel.web.dto.adrclass.AdrClassResponseDTO;

/**
 * Data Transfer Object (DTO) immutabile, implementato come Java Record, utilizzato per 
 * incapsulare e trasportare i dati in uscita (Response Payload) dal Controller REST verso il client.
 * <p>
 * <b>Pattern Architetturale: Rich DTO (Proiezione Idratata) e Asimmetria:</b><br>
 * A differenza del {@link CompatibilityRuleRequestDTO} (che adotta un design rigorosamente 
 * "piatto" a protezione delle scritture), questo DTO di risposta espone intenzionalmente 
 * gli oggetti nidificati e completi delle entità genitore ({@link AdrClass}). 
 * Questa asimmetria è una best practice del design delle API RESTful: fornendo l'anagrafica 
 * idratata (completa di descrizioni e metadati), si ottimizzano drasticamente le performance 
 * di rete. Il frontend (es. React/Angular) riceve in una singola chiamata tutto il necessario 
 * per renderizzare una tabella o una vista di dettaglio, azzerando il rischio di dover fare 
 * query HTTP aggiuntive per risolvere i singoli codici (prevenzione del N+1 Request Problem lato client).
 * </p>
 * <p>
 * <b>Immutabilità e Serializzazione:</b><br>
 * Sfruttando il costrutto {@code record} nativo di Java, l'oggetto garantisce una totale 
 * immutabilità (Thread-Safety). Questo lo rende il candidato perfetto per essere processato 
 * in sicurezza da stream paralleli e serializzato dai convertitori HTTP (come Jackson), 
 * che trasformeranno i campi in un albero JSON gerarchico, pulito e prevedibile.
 * </p>
 * @param adrClassA L'oggetto idratato rappresentante la prima classe ADR della regola, 
 * completo dei suoi attributi anagrafici (codice e descrizione).
 * @param adrClassB L'oggetto idratato rappresentante la seconda classe ADR.
 * @param isCompatible Il flag operativo di business: {@code true} (carico misto consentito) 
 * oppure {@code false} (segregazione obbligatoria).
 * @param warningNote  L'eventuale nota operativa prescrittiva destinata ai documenti di viaggio.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Read-Only Immutable View)
 * @since 1.0
 */
public record CompatibilityRuleResponseDTO(AdrClassResponseDTO adrClassA, AdrClassResponseDTO adrClassB, boolean isCompatible, String warningNote) {
	/**
	 * Factory Method statico per la conversione (Mapping) e l'aggregazione strutturata 
	 * di un'entità di dominio {@link CompatibilityRule} nel suo corrispondente 
	 * Data Transfer Object {@link CompatibilityRuleResponseDTO}.
	 * <p><b>Contesto di Dominio (Regole di Segregazione ADR):</b></p>
	 * Nel contesto logistico delle merci pericolose, questo oggetto modella le restrizioni 
	 * di carico in comune (Segregation Rules) tra due classi ADR distinte (es. liquidi 
	 * infiammabili e sostanze tossiche). Il DTO informa il client se due tipologie di 
	 * merci possono viaggiare sullo stesso veicolo o all'interno dello stesso container, 
	 * esponendo il flag di compatibilità ed eventuali deroghe o note di avvertenza legali.
	 * <p><b>Design Pattern e Composizione (Nested/Delegated Mapping):</b></p>
	 * Oltre a mappare i tipi primitivi (es. {@code isCompatible}), questo metodo brilla 
	 * per l'applicazione del pattern di <i>Delegazione</i> per la risoluzione del grafo degli oggetti (Object Graph). 
	 * Invece di spacchettare e mappare manualmente le classi ADR interne, delega l'operazione ai 
	 * rispettivi factory method ({@link AdrClassResponseDTO#fromEntity}). Questo approccio 
	 * modulare rispetta ferreamente il principio DRY (Don't Repeat Yourself) e garantisce una 
	 * coerenza assoluta nella struttura JSON in tutto l'ecosistema dell'API.
	 * <p><b>Sicurezza e Robustezza (Guard Clause / Null-Safety):</b></p>
	 * L'implementazione inizia con una rigorosa clausola di salvaguardia ({@code if(entity == null)}). 
	 * Questa pratica di programmazione difensiva assicura che le funzioni di ordine superiore 
	 * (es. mappatura di liste di regole tramite Stream API) non collassino mai a causa di una 
	 * singola relazione nulla o orfana nel database, prevenendo catastrofiche {@code NullPointerException}.
	 * @param entity L'istanza dell'entità JPA (Regola di Compatibilità) recuperata dalla base dati. 
	 * Il parametro ammette e gestisce valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link CompatibilityRuleResponseDTO}, 
	 * completamente idratata con le classi figlie annidate, oppure {@code null} se 
	 * l'entità sorgente era assente.
	 */
	public static CompatibilityRuleResponseDTO fromEntity(CompatibilityRule entity) {
		if(entity == null)
			return null;
		
		return new CompatibilityRuleResponseDTO(
			AdrClassResponseDTO.fromEntity(entity.getAdrClassA()),
			AdrClassResponseDTO.fromEntity(entity.getAdrClassB()),
			entity.isCompatible(),
			entity.getWarningNote()
		);
	}
}
