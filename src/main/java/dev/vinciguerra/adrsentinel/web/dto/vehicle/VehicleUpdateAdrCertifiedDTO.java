package dev.vinciguerra.adrsentinel.web.dto.vehicle;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) ultraleggero, progettato 
 * esclusivamente per l'aggiornamento mirato (State Toggle / Patch) dello stato 
 * di certificazione legale ADR di un veicolo.
 * <p><b>Contesto Architetturale (Granularità e PATCH Pattern):</b></p>
 * Questo record rappresenta l'implementazione ideale per le operazioni di aggiornamento 
 * parziale. Invece di costringere il client a inviare l'intera anagrafica del veicolo 
 * solo per modificare una spunta (rischiando collisioni di concorrenza se più utenti 
 * modificano lo stesso mezzo), questo payload isola il "Kill-Switch" normativo, 
 * garantendo transazioni atomiche, veloci e a bassissimo impatto di rete.
 * <p><b>Sicurezza Intrinseca (Primitive Fallback & Secure by Default):</b></p>
 * L'utilizzo del tipo primitivo {@code boolean} (invece della classe wrapper {@code Boolean}) 
 * costituisce una scelta di design difensivo di alto livello. Delega al motore di 
 * deserializzazione JSON (Jackson) la gestione dei campi mancanti: se il client invia un 
 * payload vuoto, il sistema esegue un fallback nativo assegnando il valore {@code false}. 
 * Questo approccio previene in modo assoluto le {@code NullPointerException} a runtime 
 * e implementa il principio del <i>Secure by Default</i> (un veicolo non è mai considerato 
 * certificato per merci pericolose a meno di una dichiarazione esplicita e positiva).
 * @param adrCertified Il nuovo stato legale desiderato ({@code true} = abilitato al 
 * trasporto di merci pericolose, {@code false} = non abilitato o certificazione sospesa).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record VehicleUpdateAdrCertifiedDTO(boolean adrCertified) {}
