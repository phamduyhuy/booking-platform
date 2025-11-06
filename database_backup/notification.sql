--
-- PostgreSQL database dump
--

-- Dumped from database version 17.0
-- Dumped by pg_dump version 17.5

-- Started on 2025-11-04 11:31:51

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 217 (class 1259 OID 47759)
-- Name: notification_outbox_events; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.notification_outbox_events (
    id uuid NOT NULL,
    aggregate_id character varying(100) NOT NULL,
    aggregate_type character varying(50) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    event_type character varying(100) NOT NULL,
    payload text
);


ALTER TABLE public.notification_outbox_events OWNER TO postgres;

--
-- TOC entry 218 (class 1259 OID 47766)
-- Name: notifications; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.notifications (
    notification_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by character varying(255),
    deleted_at timestamp(6) with time zone,
    deleted_by character varying(255),
    is_deleted boolean NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    updated_by character varying(255),
    content text,
    sent_at timestamp(6) with time zone,
    status character varying(50) NOT NULL,
    template_id character varying(100),
    type character varying(50) NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.notifications OWNER TO postgres;

--
-- TOC entry 3409 (class 0 OID 47759)
-- Dependencies: 217
-- Data for Name: notification_outbox_events; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.notification_outbox_events (id, aggregate_id, aggregate_type, created_at, event_type, payload) FROM stdin;
c96d806e-503e-46bb-9cb0-9fe0c423bd49	bacfa89d-debc-4008-9304-4af17ded5fd7	Notification	2025-10-21 17:36:16.80859	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 47, column 68:\\nNaming convention mismatch. Identifiers that are part of the template language (not the user specified ones) must consistently use the same naming convention within the same template. This template uses legacy naming convention (directive (tag) names are like examplename, everything else is like example_name) estabilished by auto-detection at line 47, column 54 by token \\"has_content\\", but the problematic token, \\"statusLabel\\", uses a different convention.", "status": "failed", "sentTime": "2025-10-21T17:36:16.792235246", "bookingId": "bacfa89d-debc-4008-9304-4af17ded5fd7", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
2b9adbfa-a5c0-4458-bb7a-d174045252ae	7669c8d9-0abe-4cfa-b849-4582628967b1	Notification	2025-10-21 17:40:19.360951	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 47, column 68:\\nNaming convention mismatch. Identifiers that are part of the template language (not the user specified ones) must consistently use the same naming convention within the same template. This template uses legacy naming convention (directive (tag) names are like examplename, everything else is like example_name) estabilished by auto-detection at line 47, column 54 by token \\"has_content\\", but the problematic token, \\"statusLabel\\", uses a different convention.", "status": "failed", "sentTime": "2025-10-21T17:40:19.360282083", "bookingId": "7669c8d9-0abe-4cfa-b849-4582628967b1", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
29fe5e9d-7492-4f13-aa9e-cb93d76a5101	6518b3cf-c111-41e6-aeb2-72131d01980b	Notification	2025-10-21 17:56:42.92487	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 47, column 68:\\nNaming convention mismatch. Identifiers that are part of the template language (not the user specified ones) must consistently use the same naming convention within the same template. This template uses legacy naming convention (directive (tag) names are like examplename, everything else is like example_name) estabilished by auto-detection at line 47, column 54 by token \\"has_content\\", but the problematic token, \\"statusLabel\\", uses a different convention.", "status": "failed", "sentTime": "2025-10-21T17:56:42.924410086", "bookingId": "6518b3cf-c111-41e6-aeb2-72131d01980b", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
31300cc8-bb1d-4d98-a0ef-951f1de327b1	b96a5792-ee89-4a15-97cb-36a953fa84c9	Notification	2025-10-21 17:58:58.711883	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 47, column 68:\\nNaming convention mismatch. Identifiers that are part of the template language (not the user specified ones) must consistently use the same naming convention within the same template. This template uses legacy naming convention (directive (tag) names are like examplename, everything else is like example_name) estabilished by auto-detection at line 47, column 54 by token \\"has_content\\", but the problematic token, \\"statusLabel\\", uses a different convention.", "status": "failed", "sentTime": "2025-10-21T17:58:58.711461330", "bookingId": "b96a5792-ee89-4a15-97cb-36a953fa84c9", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
c40e6c86-d263-49fb-b95c-8172cded7f27	1162df52-ad68-4e84-a7a6-3e6e5cd88b5f	Notification	2025-10-21 20:19:42.804221	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 47, column 67:\\nUnknown built-in: \\"statusLabel\\". Help (latest version): https://freemarker.apache.org/docs/ref_builtins.html; you're using FreeMarker 2.3.34.\\nThe alphabetical list of built-ins:\\nabs, absoluteTemplateName, ancestors, api, \\nblankToNull, boolean, byte, \\nc, cLowerCase, cUpperCase, capFirst, capitalize, ceiling, children, chopLinebreak, chunk, cn, contains, counter, \\ndate, dateIfUnknown, datetime, datetimeIfUnknown, default, double, dropWhile, \\nemptyToNull, endsWith, ensureEndsWith, ensureStartsWith, esc, eval, evalJson, exists, \\nfilter, first, float, floor, \\ngroups, \\nhasApi, hasContent, hasNext, html, \\nifExists, index, indexOf, int, interpret, isBoolean, isCollection, isCollectionEx, isDate, isDateLike, isDateOnly, isDatetime, isDirective, isEnumerable, isEvenItem, isFirst, isHash, isHashEx, isIndexable, isInfinite, isLast, isMacro, isMarkupOutput, isMethod, isNan, isNode, isNumber, isOddItem, isSequence, isString, isTime, isTransform, isUnknownDateLike, iso, isoH, isoHNZ, isoLocal, isoLocalH, isoLocalHNZ, isoLocalM, isoLocalMNZ, isoLocalMs, isoLocalMsNZ, isoLocalNZ, isoM, isoMNZ, isoMs, isoMsNZ, isoNZ, isoUtc, isoUtcFZ, isoUtcH, isoUtcHNZ, isoUtcM, isoUtcMNZ, isoUtcMs, isoUtcMsNZ, isoUtcNZ, itemCycle, itemParity, itemParityCap, \\njString, join, jsString, jsonString, \\nkeepAfter, keepAfterLast, keepBefore, keepBeforeLast, keys, \\nlast, lastIndexOf, leftPad, length, long, lowerAbc, lowerCase, \\nmap, markupString, matches, max, min, \\nnamespace, new, nextSibling, noEsc, nodeName, nodeNamespace, nodeType, number, numberToDate, numberToDatetime, numberToTime, \\nparent, previousSibling, \\nremoveBeginning, removeEnding, replace, reverse, rightPad, root, round, rtf, \\nseqContains, seqIndexOf, seqLastIndexOf, sequence, short, size, sort, sortBy, split, startsWith, string, substring, switch, \\ntakeWhile, then, time, timeIfUnknown, trim, trimToNull, truncate, truncateC, truncateCM, truncateM, truncateW, truncateWM, \\nuncapFirst, upperAbc, upperCase, url, urlPath, \\nvalues, \\nwebSafe, withArgs, withArgsLast, wordList, \\nxhtml, xml", "status": "failed", "sentTime": "2025-10-21T20:19:42.793911271", "bookingId": "1162df52-ad68-4e84-a7a6-3e6e5cd88b5f", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
b7a7730d-8728-4747-8543-1748136c9644	a7046d10-fcd6-45c5-892f-44076c694853	Notification	2025-10-22 07:08:58.845172	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 47, column 98:\\nUnknown built-in: \\"statusLabel\\". Help (latest version): https://freemarker.apache.org/docs/ref_builtins.html; you're using FreeMarker 2.3.34.\\nThe alphabetical list of built-ins:\\nabs, absoluteTemplateName, ancestors, api, \\nblankToNull, boolean, byte, \\nc, cLowerCase, cUpperCase, capFirst, capitalize, ceiling, children, chopLinebreak, chunk, cn, contains, counter, \\ndate, dateIfUnknown, datetime, datetimeIfUnknown, default, double, dropWhile, \\nemptyToNull, endsWith, ensureEndsWith, ensureStartsWith, esc, eval, evalJson, exists, \\nfilter, first, float, floor, \\ngroups, \\nhasApi, hasContent, hasNext, html, \\nifExists, index, indexOf, int, interpret, isBoolean, isCollection, isCollectionEx, isDate, isDateLike, isDateOnly, isDatetime, isDirective, isEnumerable, isEvenItem, isFirst, isHash, isHashEx, isIndexable, isInfinite, isLast, isMacro, isMarkupOutput, isMethod, isNan, isNode, isNumber, isOddItem, isSequence, isString, isTime, isTransform, isUnknownDateLike, iso, isoH, isoHNZ, isoLocal, isoLocalH, isoLocalHNZ, isoLocalM, isoLocalMNZ, isoLocalMs, isoLocalMsNZ, isoLocalNZ, isoM, isoMNZ, isoMs, isoMsNZ, isoNZ, isoUtc, isoUtcFZ, isoUtcH, isoUtcHNZ, isoUtcM, isoUtcMNZ, isoUtcMs, isoUtcMsNZ, isoUtcNZ, itemCycle, itemParity, itemParityCap, \\njString, join, jsString, jsonString, \\nkeepAfter, keepAfterLast, keepBefore, keepBeforeLast, keys, \\nlast, lastIndexOf, leftPad, length, long, lowerAbc, lowerCase, \\nmap, markupString, matches, max, min, \\nnamespace, new, nextSibling, noEsc, nodeName, nodeNamespace, nodeType, number, numberToDate, numberToDatetime, numberToTime, \\nparent, previousSibling, \\nremoveBeginning, removeEnding, replace, reverse, rightPad, root, round, rtf, \\nseqContains, seqIndexOf, seqLastIndexOf, sequence, short, size, sort, sortBy, split, startsWith, string, substring, switch, \\ntakeWhile, then, time, timeIfUnknown, trim, trimToNull, truncate, truncateC, truncateCM, truncateM, truncateW, truncateWM, \\nuncapFirst, upperAbc, upperCase, url, urlPath, \\nvalues, \\nwebSafe, withArgs, withArgsLast, wordList, \\nxhtml, xml", "status": "failed", "sentTime": "2025-10-22T07:08:58.840149382", "bookingId": "a7046d10-fcd6-45c5-892f-44076c694853", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
192fe267-3cc7-4c48-8d63-869fc325601e	9eb5e798-c58a-4349-ba0a-0bfe905c7765	Notification	2025-10-22 07:17:02.100947	NotificationSent	{"status": "sent", "sentTime": "2025-10-22T07:17:02.096715275", "template": "booking-confirmation.ftl", "bookingId": "9eb5e798-c58a-4349-ba0a-0bfe905c7765", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com", "bookingReference": "BK1761117397226"}
8e93d141-57ae-4bd5-8054-bb86ce0b11e9	d7fec787-38f2-416c-b47e-ff617483cb27	Notification	2025-10-22 07:40:46.903273	NotificationSent	{"status": "sent", "sentTime": "2025-10-22T07:40:46.902395290", "template": "booking-confirmation.ftl", "bookingId": "d7fec787-38f2-416c-b47e-ff617483cb27", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com", "bookingReference": "BK1761118826718"}
ce91f2e5-1f76-446d-bc57-72915e7a5d2b	c4a7fc6f-25b7-4fe4-9124-e0e39def84b4	Notification	2025-10-22 07:56:37.137728	NotificationFailed	{"error": "Syntax error in template \\"booking-confirmation.ftl\\" in line 28, column 55:\\nLexical error: encountered \\"\\\\'\\" (39), after \\"\\\\\\\\\\".", "status": "failed", "sentTime": "2025-10-22T07:56:37.130797009", "bookingId": "c4a7fc6f-25b7-4fe4-9124-e0e39def84b4", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com"}
45f25fbe-6101-4112-a668-220fb759720d	b087f9f5-aab7-458c-add1-fb68146a8b64	Notification	2025-10-24 01:13:25.680136	NotificationSent	{"status": "sent", "sentTime": "2025-10-24T01:13:25.667961569", "template": "booking-confirmation.ftl", "bookingId": "b087f9f5-aab7-458c-add1-fb68146a8b64", "eventType": "BookingConfirmed", "recipient": "huypd.dev@gmail.com", "bookingReference": "BK1761268328724"}
\.


--
-- TOC entry 3410 (class 0 OID 47766)
-- Dependencies: 218
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.notifications (notification_id, created_at, created_by, deleted_at, deleted_by, is_deleted, updated_at, updated_by, content, sent_at, status, template_id, type, user_id) FROM stdin;
\.


--
-- TOC entry 3260 (class 2606 OID 47765)
-- Name: notification_outbox_events notification_outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notification_outbox_events
    ADD CONSTRAINT notification_outbox_events_pkey PRIMARY KEY (id);


--
-- TOC entry 3262 (class 2606 OID 47772)
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (notification_id);


--
-- TOC entry 3256 (class 1259 OID 47773)
-- Name: idx_notification_outbox_aggregate; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_outbox_aggregate ON public.notification_outbox_events USING btree (aggregate_type, aggregate_id);


--
-- TOC entry 3257 (class 1259 OID 47775)
-- Name: idx_notification_outbox_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_outbox_created_at ON public.notification_outbox_events USING btree (created_at);


--
-- TOC entry 3258 (class 1259 OID 47774)
-- Name: idx_notification_outbox_event_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_outbox_event_type ON public.notification_outbox_events USING btree (event_type);


--
-- TOC entry 3408 (class 6104 OID 75078)
-- Name: dbz_publication; Type: PUBLICATION; Schema: -; Owner: postgres
--

CREATE PUBLICATION dbz_publication FOR ALL TABLES WITH (publish = 'insert, update, delete, truncate');


ALTER PUBLICATION dbz_publication OWNER TO postgres;

-- Completed on 2025-11-04 11:31:52

--
-- PostgreSQL database dump complete
--

