package dev.vinciguerra.adrsentinel.web.dto.dispatch;

import java.util.List;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorTransportMode;
import dev.vinciguerra.adrsentinel.web.annotation.dispatch.ValidatorNetWeight;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberName;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) radice (Root Object) che incapsula la richiesta 
 * di ottimizzazione e assegnazione dei carichi (Dispatch Plan) per il trasporto 
 * di merci pericolose su strada.
 * <p>
 * All'interno dell'architettura REST di AdrSentinel, questo record rappresenta il payload 
 * primario in ingresso all'endpoint di pianificazione logistica (es. {@code POST /api/v1/dispatch/plan}). 
 * Agisce come aggregatore per una distinta di base (Bill of Lading) di materie ADR eterogenee 
 * che il client desidera spedire.
 * </p>
 * <p>
 * <b>Comportamento di Validazione (Cascade Validation):</b><br>
 * L'integrità strutturale e normativa di questa richiesta è garantita dal pattern Fail-Fast 
 * nativo di Jakarta Validation:
 * <ul>
 * <li>L'annotazione {@link NotEmpty} assicura che il motore decisionale non venga mai 
 * invocato con liste di carico vuote, prevenendo cicli a vuoto e inutili query al database.</li>
 * <li>L'annotazione {@link Valid} sulla lista abilita la <b>validazione a cascata</b>: 
 * Spring ispezionerà iterativamente ogni singolo elemento della lista, applicando a sua volta 
 * i vincoli stringenti definiti all'interno di {@link OnuItemRequestDTO} 
 * (come il controllo sui pesi netti e sui gruppi di imballaggio). Se anche una sola riga 
 * del carico presenta anomalie, l'intera richiesta viene respinta con un HTTP 400 Bad Request.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Scelta Architetturale:</b> L'adozione di un {@code record} garantisce che la lista 
 * in ingresso, una volta deserializzata dal framework, rimanga immutabile durante 
 * l'attraversamento dei vari Service Layer, prevenendo alterazioni accidentali dello stato 
 * durante il calcolo dei raggruppamenti per la matrice di segregazione.
 * </p>
 * @param items La lista delle merci pericolose (materie o oggetti) richieste per la spedizione. 
 * Non può essere vuota o nulla. Ogni elemento contiene le direttive ONU, 
 * i gruppi di imballaggio e le masse nette necessarie al calcolo della 
 * Regola dei 1000 punti (ADR cap. 1.1.3.6) e delle incompatibilità.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 * @see OnuItemRequestDTO
 */
public record VehicleDispatchRequestDTO(@NotEmpty(message = "Malformed paylod: invalid argument. The list of goods cannot be empty") List<@Valid OnuItemRequestDTO> items) {
	/**
	 * Data Transfer Object (DTO) immutabile progettato per incapsulare i dati di ingresso 
	 * relativi a una singola partita di merce pericolosa (ADR) all'interno di una richiesta di spedizione.
	 * <p>
	 * In ottica Domain-Driven Design (DDD), questo record funge da livello di anti-corruzione 
	 * (Anti-Corruption Layer) ai confini dell'applicazione (Presentation Layer). Il suo scopo è 
	 * raccogliere i dati grezzi dal payload JSON del client e sottoporli a un rigoroso processo 
	 * di validazione (Fail-Fast) prima che vengano trasformati in entità di dominio e passati 
	 * al motore decisionale logistico.
	 * </p>
	 * <p>
	 * <b>Contesto Logistico (Algoritmo di Ottimizzazione):</b><br>
	 * La combinazione di questi tre campi è il requisito minimo e sufficiente per istruire 
	 * l'algoritmo di routing e matchmaking:
	 * <ul>
	 * <li>{@code onuCode} + {@code packingGroup} + {@code name}: Permettono di interrogare la matrice di segregazione 
	 * per calcolare le incompatibilità di carico (es. divieto di carico in comune) dopo aver ottenuto le OnuNumber.</li>
	 * <li>{@code packingGroup} + {@code netWeightkg}: Permettono di estrapolare la Categoria di Trasporto 
	 * e calcolare il coefficiente per l'esenzione parziale (Regola dei 1000 Punti ex cap. 1.1.3.6 ADR).</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>Scelta Architetturale:</b> L'utilizzo di un {@code record} Java garantisce la totale 
	 * immutabilità dei dati in memoria (Thread-Safety), rendendo l'oggetto ideale per la 
	 * deserializzazione concorrente tramite framework come Jackson.
	 * </p>
	 * @param onuCode Il numero ONU a 4 cifre che identifica univocamente la materia o l'oggetto 
	 * pericoloso (es. "1263" per Pitture). Validato dinamicamente da 
	 * {@link ValidatorOnuNumberCode} per garantirne la conformità formale.
	 * @param packingGroup Il Gruppo di Imballaggio associato alla merce, che ne definisce il grado 
	 * di pericolo (I = alto, II = medio, III = basso, o stringa vuota per classi 
	 * prive di G.I. come la Classe 2). Validato tramite {@link ValidatorPackingGroup}.
	 * @param name La designazione ufficiale di trasporto (Nome tecnico autorizzato dall'ADR).
	 * @param netWeightkg La quantità netta della massa pericolosa espressa in chilogrammi. 
	 * È il parametro matematico fondamentale per determinare il peso utile 
	 * sul veicolo e l'applicabilità delle esenzioni. Blindato da 
	 * {@link ValidatorNetWeight} contro valori nulli, negativi o anomalie IEEE 754 (NaN/Infinity).
	 * @param transportMode Modalità di trasporto selezionata per questa materia pericolosa (OnuNumber).
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (Strict Validated Input Payload)
	 * @since 1.0
	 */
	public record OnuItemRequestDTO(@ValidatorOnuNumberCode String onuCode, @ValidatorPackingGroup String packingGroup, @ValidatorOnuNumberName String name,
		@ValidatorNetWeight Integer netWeightkg, @ValidatorTransportMode String transportMode) {}
}
