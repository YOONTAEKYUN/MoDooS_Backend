package com.study.modoos.study.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.study.modoos.member.entity.Member;
import com.study.modoos.recruit.response.RecruitInfoResponse;
import com.study.modoos.study.entity.Category;
import com.study.modoos.study.entity.Study;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import static com.study.modoos.study.entity.QStudy.study;

@Repository
@RequiredArgsConstructor
public class StudyRepositoryImpl {

    private final JPAQueryFactory queryFactory;

    public Slice<RecruitInfoResponse> getSliceOfRecruit(Member member,
                                                        final String title,
                                                        final List<Category> categoryList,
                                                        Pageable pageable) {
        /*
        if (order.equals("likeCount")) {
            content = queryFactory
                    .selectFrom(study)
                    .where(allCond(searchCondition))
                    .orderBy(study.createdAt)
                    .fetch();

        }
        */
        JPAQuery<Study> results = queryFactory.selectFrom(study)
                .where(
                        titleLike(title),
                        categoryEq(categoryList))
                //.orderBy(study.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1);

        for (Sort.Order o : pageable.getSort()) {
            PathBuilder pathBuilder = new PathBuilder(study.getType(), study.getMetadata());
            results.orderBy(new OrderSpecifier(o.isAscending() ? Order.ASC :
                    Order.DESC, pathBuilder.get(o.getProperty())));
        }

        List<RecruitInfoResponse> contents = results.fetch()
                .stream()
                .map(o -> RecruitInfoResponse.of(o, o.isWrittenPost(member)))
                .collect(Collectors.toList());


        boolean hasNext = false;


        if (contents.size() > pageable.getPageSize()) {
            contents.remove(pageable.getPageSize());
            hasNext = true;
        }

        return new SliceImpl<>(contents, pageable, hasNext);
    }

    public Study findMaxRecruitIdx() {
        return queryFactory.selectFrom(study)
                .orderBy(study.id.desc())
                .fetchFirst();
    }

    //no-offset 방식 처리
    private BooleanExpression ltStudyId(@Nullable Long studyId) {
        return studyId == null ? null : study.id.lt(studyId);
    }


    //제목 검색어로 검색
    private BooleanExpression titleLike(final String title) {
        return StringUtils.hasText(title) ? study.title.contains(title) : null;
    }

    //카테고리로 필터링
    private BooleanBuilder categoryEq(final List<Category> categoryList) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        if (categoryList.isEmpty()) return booleanBuilder;

        for (Category category : categoryList) {
            booleanBuilder.or(study.category.eq(category));
        }

        return booleanBuilder;
    }
}