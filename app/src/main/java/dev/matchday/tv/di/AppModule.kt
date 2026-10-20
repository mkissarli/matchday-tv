package dev.matchday.tv.di

import dev.matchday.tv.feature.live.LiveViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::LiveViewModel)
}
