package com.zycu.guishop.admin;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Full daily journals, including archived history generations. Called only on the journal worker. */
public final class Trending {
    private Trending() {}
    public record Request(int days, boolean sold, String variant, String player, int offset, String asOf,long generation) { public Request(int days,boolean sold,String variant,String player,int offset,String asOf){this(days,sold,variant,player,offset,asOf,0);} }
    public record Item(String variant, String name, long quantity) {}
    public record Player(String uuid, String name, long quantity) {}
    public record Result(Request request, List<Item> items, List<Player> players, List<TransactionJournal.Entry> entries, boolean more, String warning) {}
    private static String identity(TransactionJournal.Entry entry) {
        if (entry.variant() != null) return entry.variant();
        if (entry.action().startsWith("ENCHANT_")) return "book:" + entry.listing();
        return entry.listing().replaceFirst("@[0-9a-f]{32}$", "");
    }
    public static Result query(Request request) throws Exception {
        if (request == null || !Set.of(1,7,30).contains(request.days()) || request.offset() < 0 || request.offset() > 100000 || request.variant() != null && request.variant().length()>300) throw new IllegalArgumentException("Invalid Trending query");
        if (request.player()!=null) UUID.fromString(request.player());
        Instant end = request.asOf()==null ? Instant.now() : Instant.parse(request.asOf());
        if (end.isAfter(Instant.now().plusSeconds(5))) throw new IllegalArgumentException("Invalid audit end time");
        Instant start = end.minus(request.days(),java.time.temporal.ChronoUnit.DAYS);
        request = new Request(request.days(),request.sold(),request.variant(),request.player(),request.offset(),end.toString(),request.generation());
        var items = new HashMap<String,Item>(); var players = new HashMap<String,Player>();
        var entries = new PriorityQueue<TransactionJournal.Entry>(Comparator.comparing(TransactionJournal.Entry::time)); int invalid=0;
        Path dir = TransactionJournal.directory();
        List<Path> files = List.of();
        if (Files.isDirectory(dir)) try(var paths=Files.list(dir)) { files = paths.filter(file -> file.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.jsonl"))
            .filter(file -> { var day=LocalDate.parse(file.getFileName().toString().substring(0,10)); return !day.isBefore(start.atZone(ZoneOffset.UTC).toLocalDate()) && !day.isAfter(end.atZone(ZoneOffset.UTC).toLocalDate()); }).sorted().toList(); }
        long matched=0;
        for(var file:files) try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Audit cancelled");
                TransactionJournal.Entry entry;
                try { entry=AdminDocument.JSON.fromJson(line,TransactionJournal.Entry.class); if(entry==null || entry.time()==null || entry.action()==null) throw new IllegalArgumentException(); }
                catch(RuntimeException malformed) { invalid++;continue; }
                boolean sold=Set.of("SELL","ENCHANT_SELL").contains(entry.action()), bought=Set.of("BUY","ENCHANT_BUY").contains(entry.action());
                if(!(request.sold()?sold:bought) || entry.quantity()<=0) continue;
                Instant time; try { time=Instant.parse(entry.time()); } catch(RuntimeException malformed) { invalid++;continue; }
                if(time.isBefore(start) || time.isAfter(end))continue;
                try { UUID.fromString(entry.uuid()); if(entry.listing()==null)throw new IllegalArgumentException(); } catch(RuntimeException malformed){invalid++;continue;}
                String id=identity(entry); if(request.variant()!=null && !request.variant().equals(id))continue;
                if(request.player()!=null && !request.player().equals(entry.uuid()))continue;
                if(request.variant()==null) {
                    var old=items.get(id); String name=entry.itemName()==null?entry.listing():entry.itemName();
                    items.put(id,new Item(id,name,Math.addExact(old==null?0:old.quantity(),entry.quantity())));
                    if(items.size()>100000)throw new IllegalStateException("Audit exceeded 100,000 distinct variants; no partial ranking was returned");
                } else if(request.player()==null) {
                    var old=players.get(entry.uuid()); players.put(entry.uuid(),new Player(entry.uuid(),entry.player()==null||entry.player().isBlank()?entry.uuid():entry.player(),Math.addExact(old==null?0:old.quantity(),entry.quantity())));
                    if(players.size()>100000)throw new IllegalStateException("Audit exceeded 100,000 players; no partial ranking was returned");
                } else {
                    matched++;entries.add(entry); if(entries.size()>request.offset()+101)entries.poll();
                }
            }
        }
        var rows=entries.stream().sorted(Comparator.comparing(TransactionJournal.Entry::time).reversed().thenComparing(entry -> Objects.toString(entry.operationId(),""))).skip(request.offset()).limit(100).toList();
        return new Result(request,items.values().stream().sorted(Comparator.comparingLong(Item::quantity).reversed().thenComparing(Item::variant)).limit(20).toList(),players.values().stream().sorted(Comparator.comparingLong(Player::quantity).reversed().thenComparing(Player::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Player::uuid)).skip(request.offset()).limit(100).toList(),rows,request.variant()!=null&&request.player()==null?players.size()>request.offset()+100:matched>request.offset()+100,invalid==0?"":invalid+" malformed journal lines skipped; totals may be incomplete. Original files are preserved.");
    }
}
