package vn.fs.service;

import java.util.*;
import vn.fs.entity.BookProfile;
import vn.fs.entity.Product;

/** Additional relevance from approved editorial fields; never changes eligibility. */
final class BookRelevanceScorer {
    private static final Set<String> STOP_WORDS=Set.of("sach","cuon","quyen","truyen","cho","toi","minh","tim","ve","mot","nhung","cac","cua","va","co","can","muon","doc","duoi","tren","ngan","gia","nguoi","the","nao","giup","voi","de","nhe","gi","nay","ban");
    private final ChatIntentService language;
    BookRelevanceScorer(ChatIntentService language){this.language=language;}

    double score(String query,Product product,BookProfile profile,List<String> topicNames) {
        if(profile==null || !profile.isApproved())return 0;
        Set<String> terms=terms(query);
        if(terms.isEmpty())return 0;
        // The same evidence cannot accumulate points by repeating tags in a group.
        Map<String,Double> best=new HashMap<>();
        Set<String> documented=new HashSet<>();
        for(String value:List.of(Objects.toString(product.getName(),""),Objects.toString(profile.getSummary(),""),
                Objects.toString(profile.getLearningGoal(),""),Objects.toString(profile.getAudience(),""),Objects.toString(profile.getReadingStyle(),"")))
            documented.addAll(terms(value));
        for(String name:topicNames) {
            int colon=name.indexOf(':');if(colon<0)continue;
            String group=language.normalize(name.substring(0,colon));
            double weight=switch(group) {
                case "chu de" -> 90;
                case "muc dich" -> 65;
                case "the loai","dang noi dung" -> 45;
                case "doc gia" -> 15;
                case "cach trinh bay" -> 10;
                case "muc doc" -> 20;
                default -> 0;
            };
            if(weight==0)continue;
            for(String alias:name.substring(colon+1).split("/")) {
                documented.addAll(terms(alias));
                double value=overlap(terms,alias,weight);
                best.merge(group,value,Math::max);
            }
        }
        double coverage=(double)terms.stream().filter(documented::contains).count()/terms.size();
        return 120*coverage*coverage+best.values().stream().mapToDouble(Double::doubleValue).sum()
            +overlap(terms,product.getName(),25)
            +overlap(terms,profile.getSummary(),15)
            +overlap(terms,profile.getLearningGoal(),25)
            +overlap(terms,profile.getAudience(),8)
            +overlap(terms,profile.getReadingStyle(),5);
    }

    private Set<String> terms(String value) {
        Set<String> result=new LinkedHashSet<>();
        for(String token:language.normalize(value==null?"":value).split("[^\\p{L}\\p{N}]+"))
            if(token.length()>=2 && !token.matches("\\d+") && !STOP_WORDS.contains(token))result.add(token);
        return result;
    }

    private double overlap(Set<String> query,String value,double weight) {
        Set<String> field=terms(value);
        long hits=query.stream().filter(field::contains).count();
        if(hits==0)return 0;
        double coverage=(double)hits/query.size();
        // A fragment of a long request must not outweigh a complete subject match.
        double result=weight*coverage*coverage;
        String queryPhrase=String.join(" ",query),fieldPhrase=String.join(" ",field);
        if(query.size()>=2 && ChatCatalogQuery.word(fieldPhrase,queryPhrase))result+=weight*0.5;
        else if(query.size()>=2 && hits==query.size())result+=weight*0.25;
        else if(field.size()>=2 && ChatCatalogQuery.word(queryPhrase,fieldPhrase))
            result+=weight*0.2*coverage;
        return result;
    }
}
