/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.modification.*;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.user.Organization;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles(Initializer.PROFILE_DEV)
public class EventManagerIntegrationTest {

    public static final int AVAILABLE_SEATS = 20;

    @BeforeClass
    public static void initEnv() {
        System.setProperty("datasource.dialect", "HSQLDB");
        System.setProperty("datasource.driver", "org.hsqldb.jdbcDriver");
        System.setProperty("datasource.url", "jdbc:hsqldb:mem:alfio");
        System.setProperty("datasource.username", "sa");
        System.setProperty("datasource.password", "");
        System.setProperty("datasource.validationQuery", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
    }

    @Autowired
    private EventManager eventManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketRepository ticketRepository;


    private Pair<Event, String> initEvent(List<TicketCategoryModification> categories) {
        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();
        String eventName = UUID.randomUUID().toString();

        organizationRepository.create(organizationName, "org", "email@example.com");
        Organization organization = organizationRepository.findByName(organizationName).get(0);
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com");

        EventModification em = new EventModification(null, "url", "url", "url", null,
                eventName, organization.getId(),
                "muh location", "muh description",
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now().plusHours(1)),
                BigDecimal.TEN, "CHF", AVAILABLE_SEATS, BigDecimal.ONE, true, null, categories, false, new LocationDescriptor("","","",""));
        eventManager.createEvent(em);
        return Pair.of(eventManager.getSingleEvent(eventName, username), username);
    }

    @Test
    public void testUnboundedTicketsGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
    }

    @Test
    public void testEventGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true));
        Event event = initEvent(categories).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testUnboundedEventGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(AVAILABLE_SEATS, tickets.stream().filter(t -> t.getCategoryId() == null).count());
        EventWithStatistics eventWithStatistics = eventManager.fillWithStatistics(event);
        List<TicketCategoryWithStatistic> ticketCategories = eventWithStatistics.getTicketCategories();
        assertEquals(1, ticketCategories.size());
        assertEquals(0, ticketCategories.get(0).getMaxTickets());
    }

    @Test
    public void testEventGenerationWithUnboundedCategory() {
        List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true),
                new TicketCategoryModification(null, "default", 9,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true),
                new TicketCategoryModification(null, "default", 0,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(1, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEventGenerationWithOverflow() {
        List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true),
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true),
                new TicketCategoryModification(null, "default", 0,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertTrue(tickets.stream().noneMatch(t -> t.getCategoryId() == null));
    }

    @Test
    public void testAddUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                "desc", BigDecimal.TEN, false, "", false);
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testIncreaseEventSeatsWithAnUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 40, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null);
        eventManager.updateEventPrices(event.getId(), update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(40, tickets.size());
        assertEquals(40, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testIncreaseEventSeatsWithABoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 40, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null);
        eventManager.updateEventPrices(event.getId(), update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(40, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() != null).count());
    }

    @Test
    public void testDecreaseEventSeatsWithAnUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 10, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null);
        eventManager.updateEventPrices(event.getId(), update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testDecreaseEventSeatsWithABoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 10, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null);
        eventManager.updateEventPrices(event.getId(), update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() != null));
    }

    @Test
    public void testAddBoundedCategoryToUnboundedEvent() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 0,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                "desc", BigDecimal.TEN, false, "", true);
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testUpdateBoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories);
        Event event = pair.getKey();
        TicketCategoryWithStatistic category = eventManager.fillWithStatistics(event).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), "default", 20,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                "desc", BigDecimal.TEN, false, "", false);
        eventManager.updateCategory(category.getId(), event.getId(), tcm, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(0, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }


}