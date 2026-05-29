package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorETA;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del motore di validazione per il vincolo temporale e logistico {@link ValidatorETA}.
 * <p>
 * Questa classe viene istanziata e invocata automaticamente dal framework di validazione 
 * (es. Hibernate Validator) per analizzare i campi annotati con {@code @ValidatorETA}. 
 * Agisce come un rigoroso filtro di <b>Defensive Programming</b>, assicurando che i tempi 
 * di percorrenza stradale calcolati dai motori cartografici esterni (come OpenRouteService) 
 * siano conformi alle leggi della fisica e ai limiti architetturali del sistema.
 * </p>
 * <p>
 * <b>Responsabilità Architetturali:</b>
 * <ul>
 * <li><b>Strict Nullability:</b> Discostandosi dalle direttive standard JSR-380 (che tollerano i valori assenti), 
 * questo validatore impone un paradigma "Strict", intercettando e respingendo i valori {@code null}. 
 * Questo centralizza la regola di obbligatorietà e la regola di dominio in un singolo componente.</li>
 * <li><b>Domain-Safety (Limite Inferiore):</b> Garantisce la coerenza del dominio spaziotemporale, 
 * impedendo tempi negativi o pari a zero (che equivarrebbero a viaggi istantanei o paradossi temporali).</li>
 * <li><b>System-Safety (Limite Superiore / Sanity Check):</b> Pone un tetto massimo invalicabile di 
 * 43.200 minuti (30 giorni). Questa barriera protegge le logiche a valle (es. le istruzioni 
 * {@code LocalDateTime.plusMinutes()}) da attacchi malevoli o da bug dell'API cartografica che 
 * potrebbero tentare di causare un {@code Integer Overflow}.
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorETA
 */
public class ETAValidator implements ConstraintValidator<ValidatorETA, Integer> {
	/**
	 * Valuta se il tempo stimato di arrivo (ETA) rispetta i vincoli fisici, operativi e di sicurezza del sistema.
	 * <p>
	 * <b>Flusso di Validazione (Execution Flow):</b>
	 * <ol>
	 * <li><b>Tolleranza Zero (Strict Mode):</b> Verifica l'assenza del dato. Se il valore è {@code null}, 
	 * il metodo restituisce immediatamente {@code false}, bloccando il payload.</li>
	 * <li><b>Integrità del Range Temporale:</b> Verifica simultaneamente che il tempo sia strettamente maggiore 
	 * di 0 (tempo fisico valido) e minore o uguale a 43.200 minuti (limite di sicurezza corrispondente a 30 giorni 
	 * di viaggio ininterrotto).</li>
	 * </ol>
	 * </p>
	 * @param value L'istanza numerica di {@link Integer} rappresentante i minuti di viaggio stimati (può essere null).
	 * @param context Il contesto di validazione fornito dal framework, utilizzabile per sovrascrivere 
	 * dinamicamente il template del messaggio di errore (non alterato in questa implementazione).
	 * @return {@code false} se il valore è nullo, zero, negativo o eccedente il tetto di sicurezza di 30 giorni; 
	 * {@code true} se l'orizzonte temporale è perfettamente valido e processabile.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value > 0 && value <= 43200;
	}
}
