package dev.vinciguerra.adrsentinel.web.dto.compatibilityrule;

import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;

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
 * @param id L'identificativo fisico (Primary Key) generato dal database. Esporre 
 * questo campo è fondamentale per consentire al frontend di mantenere 
 * lo stato e di puntare alla risorsa esatta in caso di future operazioni 
 * di mutazione (es. {@code PUT /compatibility-rules/{id}} o {@code DELETE}).
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
public record CompatibilityRuleResponseDTO(Long id, AdrClass adrClassA, AdrClass adrClassB, boolean isCompatible, String warningNote) {}
